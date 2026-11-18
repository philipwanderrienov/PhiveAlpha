#!/usr/bin/env python3
"""Opt-in loopback-only Phase 1.5 HTTP/process harness; no production configuration."""
import argparse
import base64
import concurrent.futures
import datetime as dt
import hashlib
import http.server
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import tempfile
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def request(base, path, method="GET"):
    req = urllib.request.Request(base + path, data=b"{}" if method == "POST" else None, method=method)
    with OPENER.open(req, timeout=25) as r:
        return json.load(r)


def wait_for(fn, timeout=45):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            value = fn()
            if value:
                return value
        except (OSError, ValueError):
            pass
        time.sleep(0.1)
    raise AssertionError("Timed out waiting for isolated test condition")


class Mock(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    mode = 503
    delay = 0
    retry = "7200"
    calls = []
    lock = threading.Lock()
    stop = threading.Event()

    def log_message(self, *args):
        pass

    def respond(self, status, body, headers=None):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_POST(self):
        assert self.path == "/v1beta/models/gemini-e2e-mock:generateContent", self.path
        assert self.headers["x-goog-api-key"] == "e2e-placeholder-not-a-credential"
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        articles = json.loads(body["contents"][0]["parts"][0]["text"])["articles"]
        assert 1 <= len(articles) <= 5
        assert all(set(a) == {"newsId", "title", "source", "publishedAt"} for a in articles)
        with self.lock:
            status, delay, retry = self.mode, self.delay, self.retry
            self.calls.append({"status": status, "time": time.monotonic(), "ids": [a["newsId"] for a in articles]})
        time.sleep(delay)
        if status == 200:
            items = [dict(newsId=a["newsId"], relevance=0.9, sentiment="BULLISH", impact="HIGH", rationale="Synthetic public headline") for a in articles]
            result = {"candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": json.dumps({"items": items})}]}}]}
        else:
            result = {"error": "mock failure; never a real provider response"}
        self.respond(status, result, {"Retry-After": retry} if status == 429 else {})

    def do_GET(self):
        if self.headers.get("Upgrade", "").lower() == "websocket":
            self.websocket()
        elif self.path.startswith("/api/v3/klines"):
            # Bootstrap is deliberately empty; synthetic history is seeded through test controls.
            self.respond(200, [])
        else:
            self.respond(200, [])

    def websocket(self):
        key = self.headers["Sec-WebSocket-Key"] + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        self.send_response(101)
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", base64.b64encode(hashlib.sha1(key.encode()).digest()).decode())
        self.end_headers()
        sequence = 0
        try:
            while not self.stop.wait(0.2):
                sequence += 1
                now = int(time.time() * 1000)
                price = str(70000 + sequence)
                data = dict(s="BTCUSDT", E=now)
                if "miniTicker" in self.path:
                    data.update(c=price, o="70000", h="71000", l="69000", v="100", q="7000000")
                elif "bookTicker" in self.path:
                    data.update(b=str(int(price)-1), a=str(int(price)+1), B="1", A="1")
                elif "aggTrade" in self.path:
                    data.update(p=price, q="0.01", m=False, a=sequence, T=now)
                else:
                    data["k"] = dict(i="1m", t=now//60000*60000, T=now//60000*60000+59999,
                                     o=price, h=str(int(price)+10), l=str(int(price)-10), c=price, v="10", q="700000", n=100, x=False)
                payload = json.dumps({"stream": "synthetic", "data": data}).encode()
                frame = bytes([0x81, 126]) + struct.pack("!H", len(payload)) + payload if len(payload) >= 126 else bytes([0x81, len(payload)]) + payload
                self.wfile.write(frame)
                self.wfile.flush()
        except (OSError, ValueError):
            pass
        self.close_connection = True


class StaticProxy(http.server.SimpleHTTPRequestHandler):
    backend = ""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT / "frontend/dist/coinmind-web/browser"), **kwargs)

    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path.startswith(("/api/", "/actuator/")):
            if self.path.endswith("/stream"):
                self.send_error(503, "Browser SSE disabled; backend SSE tested separately")
                return
            try:
                with OPENER.open(self.backend + self.path, timeout=10) as response:
                    data = response.read()
                    self.send_response(response.status)
                    self.send_header("Content-Type", response.headers.get("Content-Type", "application/json"))
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
            except OSError:
                self.send_error(503)
        else:
            super().do_GET()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--confirm", required=True, choices=["LOOPBACK_MOCKS_ONLY"])
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--browser-only-from", type=Path, help="Reuse a completed backend run's captured HTTP states")
    args = parser.parse_args()
    artifact = args.browser_only_from.resolve() if args.browser_only_from else Path(tempfile.mkdtemp(prefix="coinmind-phase15-e2e-"))
    if not artifact.is_relative_to("/tmp") or not artifact.name.startswith("coinmind-phase15-e2e-"):
        raise ValueError("Only dedicated /tmp E2E artifacts may be reused")
    print("ARTIFACTS", artifact, flush=True)
    report = {"artifactDirectory": str(artifact), "checks": [], "snapshots": {}, "externalProviderCalls": 0}
    if args.browser_only_from:
        report = json.loads((artifact / "report.json").read_text())
        report["previousHarnessFailure"] = report.pop("error", None)
    (Path("/tmp") / "coinmind-phase15-e2e-latest.json").write_text(json.dumps({"artifactDirectory": str(artifact)}))
    processes = []
    servers = []
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    mock = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Mock)
    mock.daemon_threads = True
    servers.append(mock)
    threading.Thread(target=mock.serve_forever, daemon=True).start()
    mock_port = mock.server_address[1]
    xml = ROOT / "backend/target/surefire-reports/TEST-com.coinmind.ai.provider.GeminiAiProviderTest.xml"
    cp = next(p.attrib["value"] for p in ET.parse(xml).findall("properties/property") if p.attrib["name"] == "java.class.path")
    env = {k: v for k, v in os.environ.items() if k in ("PATH", "HOME", "JAVA_HOME", "LANG", "LC_ALL")}
    env["COINMIND_E2E_CONFIRM"] = args.confirm

    def check(name, **evidence):
        report["checks"].append(dict(name=name, **evidence))
        print("PASS", name, json.dumps(evidence), flush=True)

    def start(name, ledger=None, quota=10, at=None, expect_failure=False):
        state = ledger or artifact / name
        backend_port = port()
        log = artifact / (name + ".log")
        with log.open("w") as output:
            p = subprocess.Popen(["java", "-cp", cp, "com.coinmind.ai.provider.GeminiRecoveryE2eApplication",
                                  str(backend_port), str(mock_port), str(state), (at or now).isoformat(), str(quota)],
                                 cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
        processes.append(p)
        base = "http://127.0.0.1:" + str(backend_port)
        if expect_failure:
            p.wait(timeout=45)
            assert p.returncode != 0 and "Gemini durable ledger unavailable" in log.read_text()
            return base, p, state
        def ready():
            if p.poll() is not None:
                raise AssertionError("Isolated JVM exited: " + str(log))
            return request(base, "/actuator/health").get("status") == "UP"
        wait_for(ready)
        request(base, "/__e2e/market/1", "POST")
        return base, p, state

    def stop(p, crash=False):
        if p.poll() is None:
            p.kill() if crash else p.terminate()
            try:
                p.wait(timeout=15)
            except subprocess.TimeoutExpired:
                p.kill()
                p.wait(timeout=10)

    def status(base, key=None):
        result = request(base, "/api/v1/ai/status")
        assert "e2e-placeholder" not in json.dumps(result)
        if key:
            report["snapshots"][key] = result
        return result

    def advance(base, seconds):
        result = request(base, "/__e2e/advance/" + str(seconds), "POST")
        return dt.datetime.fromisoformat(result["now"].replace("Z", "+00:00"))

    def recover(base):
        request(base, "/__e2e/recover", "POST")

    def analyze(base):
        return request(base, "/api/v1/ai/news-intelligence/BTCUSDT", "POST")

    def run_browser(base):
        StaticProxy.backend = base
        ui = http.server.ThreadingHTTPServer(("127.0.0.1", 0), StaticProxy)
        ui.daemon_threads = True
        servers.append(ui)
        threading.Thread(target=ui.serve_forever, daemon=True).start()
        ui_env = dict(env, COINMIND_E2E_UI_URL="http://127.0.0.1:"+str(ui.server_address[1]),
                      COINMIND_E2E_ARTIFACTS=str(artifact), PLAYWRIGHT_BROWSERS_PATH="/tmp/coinmind-phase15-browsers")
        node = "/home/phive/.nvm/versions/node/v22.23.2/bin/node"
        subprocess.run([node, str(ROOT / "frontend/test-ai-status-browser.mjs")], env=ui_env, check=True, timeout=160)
        check("production Angular browser contract, responsiveness and status accessibility", evidence="browser-results.json")

    try:
        if args.browser_only_from:
            base, process, _ = start("browser-recheck", artifact / "http-403")
            assert status(base)["state"] == "AUTH_ERROR"
            run_browser(base)
            report["result"] = "PASS"
            return
        base, process, ledger = start("recovery")
        assert status(base, "unverified")["state"] == "CONFIGURED_UNVERIFIED"
        request(base, "/__e2e/news/a", "POST")
        assert analyze(base)["direction"] == "UNAVAILABLE"
        opened = status(base, "open")
        assert opened["state"] == "ERROR" and opened["recovery"]["state"] == "OPEN"
        assert dt.datetime.fromisoformat(opened["recovery"]["nextEligibleRecoveryTime"].replace("Z", "+00:00")) == now + dt.timedelta(seconds=300)
        count = len(Mock.calls)
        advance(base, 299)
        analyze(base)
        recover(base)
        time.sleep(0.5)
        assert len(Mock.calls) == count
        check("503 OPEN and cooldown blocks all HTTP", calls=count, cooldownSeconds=300)
        Mock.mode, Mock.delay = 200, 8
        advanced = advance(base, 1)
        # Do not call recover: observe the real production @Scheduled fixedDelay=30000 tick.
        waited = time.monotonic()
        wait_for(lambda: len(Mock.calls) == count + 1, timeout=36)
        half = status(base, "half_open")
        assert half["recovery"]["state"] == "HALF_OPEN" and half["state"] == "ERROR"
        check("real 30-second scheduler starts HALF_OPEN", observedWaitSeconds=round(time.monotonic()-waited, 3))
        # A real SSE subscriber and real WS fixture publications keep flowing while Gemini is delayed.
        def sse():
            req = urllib.request.Request(base + "/api/v1/market/tickers/stream", headers={"Accept": "text/event-stream"})
            values = []
            with OPENER.open(req, timeout=3) as response:
                while len(values) < 3:
                    line = response.readline().decode().strip()
                    if line.startswith("data:"):
                        values.append(json.loads(line[5:])["closePrice"])
            assert len(set(values)) >= 2
            return values
        with concurrent.futures.ThreadPoolExecutor() as pool:
            streamed = pool.submit(sse)
            latencies = []
            for _ in range(5):
                started = time.monotonic()
                tickers = request(base, "/api/v1/market/tickers")
                setup = request(base, "/api/v1/trade/setup/BTCUSDT/1m")
                assert tickers and setup["action"] in ["WAIT", "WATCH_BUY", "WATCH_SELL", "BUY", "SELL"]
                assert any("deterministik" in w for w in setup["warnings"])
                latencies.append(round(time.monotonic()-started, 3))
                analyze(base)
                recover(base)
            values = streamed.result(timeout=4)
        assert len(Mock.calls) == count + 1
        check("slow Gemini does not block WS -> SSE, market HTTP or deterministic advisor", ssePrices=values, pairedHttpSeconds=latencies)
        wait_for(lambda: status(base)["state"] == "READY", timeout=12)
        status(base, "ready")
        assert analyze(base)["confidence"] == 0
        check("validated 200 closes circuit; cache and in-flight admission deduplicate", calls=len(Mock.calls))
        Mock.delay, Mock.mode = 0, 503
        advance(base, 61)
        request(base, "/__e2e/news/b", "POST")
        analyze(base)
        advance(base, 300)
        before = len(Mock.calls)
        recover(base)
        wait_for(lambda: len(Mock.calls) == before + 1 and status(base)["recovery"]["state"] == "OPEN")
        repeated = status(base, "repeated_503")
        deadline = dt.datetime.fromisoformat(repeated["recovery"]["nextEligibleRecoveryTime"].replace("Z", "+00:00"))
        fake_now = advance(base, 0)
        delay = (deadline - fake_now).total_seconds()
        assert 600 <= delay < 720
        check("repeated 503 doubles backoff with bounded jitter", cooldownSeconds=delay)
        calls_before = len(Mock.calls)
        stop(process)
        base, process, _ = start("restart-open", ledger, at=fake_now)
        assert status(base)["recovery"] == repeated["recovery"]
        request(base, "/__e2e/news/c", "POST")
        analyze(base)
        assert len(Mock.calls) == calls_before
        check("separate JVM restart preserves OPEN deadline and failures", attempts=len(Mock.calls))
        # Same ledger refuses a concurrent JVM rather than resetting accounting.
        start("same-ledger-contender", ledger, at=fake_now, expect_failure=True)
        check("second JVM sharing ledger is rejected by lifetime process lock")
        # Kill a real in-flight HALF_OPEN process. Its one-hour pre-request deadline survives.
        Mock.mode, Mock.delay = 200, 12
        crash_now = advance(base, int(delay))
        recover(base)
        wait_for(lambda: status(base)["recovery"]["state"] == "HALF_OPEN")
        crash_status = status(base)
        stop(process, crash=True)
        Mock.delay = 0
        base, process, _ = start("restart-half-open", ledger, at=crash_now)
        restored = status(base)
        assert restored["recovery"]["state"] == "OPEN"
        assert restored["recovery"]["nextEligibleRecoveryTime"] == crash_status["recovery"]["nextEligibleRecoveryTime"]
        check("killed HALF_OPEN JVM restores OPEN with conservative one-hour deadline")
        stop(process)
        # A lower cap after restart must retain every failed attempt.
        Mock.mode = 503
        base, process, quota_ledger = start("quota", quota=1)
        request(base, "/__e2e/news/q", "POST")
        analyze(base)
        assert status(base, "quota")["state"] == "RATE_LIMITED"
        stop(process)
        count = len(Mock.calls)
        base, process, _ = start("quota-restart", quota_ledger, quota=1, at=now+dt.timedelta(seconds=3600))
        request(base, "/__e2e/news/q2", "POST")
        analyze(base)
        assert status(base)["state"] == "RATE_LIMITED" and len(Mock.calls) == count
        check("rolling quota survives separate-process restart including failed attempt")
        other, other_process, _ = start("independent-ledger-hazard", quota=1)
        request(other, "/__e2e/news/independent", "POST")
        analyze(other)
        assert len(Mock.calls) == count + 1
        check("hazard reproduced: separate ledger permits another attempt for same mock key; single-instance constraint required")
        stop(other_process)
        stop(process)
        for code in (429, 401, 403):
            Mock.mode, Mock.delay = code, 0
            base, process, auth_ledger = start("http-" + str(code))
            request(base, "/__e2e/news/f" + str(code), "POST")
            analyze(base)
            response = status(base, str(code))
            count = len(Mock.calls)
            if code == 429:
                assert response["recovery"]["nextEligibleRecoveryTime"] == (now+dt.timedelta(seconds=7200)).isoformat().replace("+00:00", "Z")
                advance(base, 7199)
            else:
                assert response["state"] == "AUTH_ERROR"
                advance(base, 86401)
            recover(base)
            analyze(base)
            assert len(Mock.calls) == count
            check(str(code) + (" Retry-After prevents early probe" if code == 429 else " auth latch prevents automatic attempts"))
            if code in (401, 403):
                stop(process)
                base, process, _ = start("auth-restart-" + str(code), auth_ledger, at=now+dt.timedelta(seconds=86401))
                assert status(base)["state"] == "AUTH_ERROR"
                request(base, "/__e2e/news/auth-restart", "POST")
                analyze(base)
                assert len(Mock.calls) == count
                check(str(code) + " auth latch survives JVM restart")
            if code != 403:
                stop(process)
        (artifact / "status-snapshots.json").write_text(json.dumps(report["snapshots"], indent=2))
        report["mockCalls"] = Mock.calls
        if args.browser:
            run_browser(base)
        report["result"] = "PASS"
    except BaseException as error:
        report["result"] = "FAIL"
        report["error"] = str(error)
        raise
    finally:
        for p in processes:
            stop(p)
        Mock.stop.set()
        for server in servers:
            server.shutdown()
            server.server_close()
        report["allChildJvmsStopped"] = all(p.poll() is not None for p in processes)
        (artifact / "report.json").write_text(json.dumps(report, indent=2))
        print("REPORT", artifact / "report.json", flush=True)


if __name__ == "__main__":
    main()
