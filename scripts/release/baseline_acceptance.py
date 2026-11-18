#!/usr/bin/env python3
"""Build/test a configuration-only conservative baseline in NEW /tmp storage.

Uses the existing verified candidate; never invokes systemd, git or live providers.
All child JVMs receive a minimal environment, loopback dependencies and temporary state.
"""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import urllib.parse
import urllib.error
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


POLICY = {
    'enabled: ${AI_ENABLED:false}': 'enabled: false',
    'provider: ${AI_PROVIDER:openai}': 'provider: gemini',
    'news-enabled: ${GEMINI_NEWS_ENABLED:false}': 'news-enabled: false',
    'api-key: ${GEMINI_API_KEY:}': 'api-key: ""',
    'api-key: ${AI_API_KEY:}': 'api-key: ""',
    'automatic-enabled: ${AI_AUTOMATIC_ENABLED:false}': 'automatic-enabled: false',
}

# Same-package fixture calls the EXACT packaged recovery class, with no HTTP client.
PROBE = r'''
package com.coinmind.ai.provider;
import java.nio.file.*;
import java.time.*;
public class BaselineLedgerProbe {
  static void require(boolean value) { if (!value) throw new AssertionError("ledger invariant"); }
  public static void main(String[] a) throws Exception {
    Path path=Path.of(a[0]); Instant t=Instant.parse(a[1]); String action=a[2];
    if (!path.toAbsolutePath().startsWith("/tmp")) throw new IllegalArgumentException();
    if (action.startsWith("seed-")) {
      require(!Files.exists(path.resolve("gemini.properties")));
      try(var s=new GeminiRecoveryState(Clock.fixed(t.minusSeconds(120),ZoneOffset.UTC),path)) {
        require(s.admit(10));
        if(action.equals("seed-open") || action.equals("seed-half")) s.failed("HTTP_SERVER_ERROR",503,null);
        else if(action.equals("seed-auth")) s.failed("HTTP_AUTH_ERROR",401,null);
        else s.completed();
      }
      if(action.equals("seed-quota")) try(var s=new GeminiRecoveryState(Clock.fixed(t.minusSeconds(60),ZoneOffset.UTC),path)) { require(s.admit(10)); s.completed(); }
      if(action.equals("seed-half")) try(var s=new GeminiRecoveryState(Clock.fixed(t.plusSeconds(181),ZoneOffset.UTC),path)) { require(s.admit(10)); }
    } else {
      try(var s=new GeminiRecoveryState(Clock.fixed(t,ZoneOffset.UTC),path)) {
        switch(action) {
          case "open" -> { require(s.circuit.equals("OPEN")); require(s.calls.size()==1); require(!s.admit(10)); }
          case "auth" -> { require(s.circuit.equals("AUTH_ERROR")); require(s.calls.size()==1); require(!s.admit(10)); }
          case "quota" -> { require(s.calls.size()==2); require(!s.admit(2)); }
          case "half" -> { require(s.circuit.equals("OPEN")); require(s.calls.size()==2); require(!s.admit(10)); }
          case "recover" -> { require(s.circuit.equals("OPEN")); require(s.calls.size()==1); require(s.admit(10)); s.completed(); require(s.calls.size()==2); }
          case "recovered" -> { require(s.circuit.equals("CLOSED")); require(s.success!=null); require(s.calls.size()==2); require(!s.admit(2)); }
          default -> throw new IllegalArgumentException(action);
        }
      }
    }
    System.out.println("PASS "+action);
  }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--confirm', choices=['SYNTHETIC_LEDGER_LOOPBACK_ONLY'], required=True)
    args = parser.parse_args()
    candidate = args.candidate.absolute()
    if candidate.is_symlink() or not (candidate.is_relative_to('/tmp') or
                                     candidate.parent == Path('/opt/coinmind/releases')):
        parser.error('candidate must be a real /tmp or imported release bundle (read only)')
    verify = module('release_verify', ROOT / 'scripts/release/activate.py').verify
    original = verify(candidate)
    e2e = module('loopback_fixture', ROOT / 'scripts/validation/phase15_e2e.py')
    artifact = Path(tempfile.mkdtemp(prefix='coinmind-baseline-acceptance-'))
    print('ARTIFACTS', artifact, flush=True)
    report = {'directory': str(artifact), 'checks': [], 'status': 'BLOCKED'}
    processes = []
    server = None
    original_hashes = {n: sha(candidate / n) for n in ['manifest.json', *original['files']]}
    # Never inherit application secrets, JVM options, external config, or proxy variables.
    env = {k: os.environ[k] for k in ('PATH', 'JAVA_HOME', 'LANG', 'LC_ALL') if k in os.environ}
    env['HOME'] = str(artifact)
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)

    def check(name, **evidence):
        report['checks'].append({'name': name, **evidence})
        print('PASS', name, json.dumps(evidence), flush=True)

    def stop(proc):
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=10)
                raise AssertionError('graceful child shutdown timed out')

    try:
        baseline = artifact / 'baseline'
        baseline.mkdir()
        with zipfile.ZipFile(candidate / 'backend.jar') as src, zipfile.ZipFile(baseline / 'backend.jar', 'w') as dst:
            for info in src.infolist():
                data = src.read(info.filename)
                if info.filename == 'BOOT-INF/classes/application.yml':
                    config = data.decode()
                    for old, new in POLICY.items():
                        assert config.count(old) == 1, old
                        config = config.replace(old, new)
                    data = config.encode()
                dst.writestr(info, data)
        shutil.copytree(candidate / 'frontend', baseline / 'frontend')
        provenance = {'derived_from': str(candidate), 'candidate_manifest_sha256': original_hashes['manifest.json'],
                      'build': 'configuration-only JAR repack; no recompile', 'embedded_configuration_changes': POLICY,
                      'required_runtime': 'gemini provider selected, absolute shared ledger, AI/news/automatic AI disabled; no overriding enabling properties'}
        (baseline / 'baseline-policy.json').write_text(json.dumps(provenance, indent=2) + '\n')
        manifest = dict(original)
        manifest['files'] = {str(f.relative_to(baseline)): sha(f) for f in sorted(baseline.rglob('*')) if f.is_file()}
        manifest['baseline_derivation'] = provenance
        (baseline / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        verify(baseline)
        jars = {'baseline': baseline / 'backend.jar', 'candidate': candidate / 'backend.jar'}
        ref = zipfile.ZipFile(jars['candidate'])
        app_classes = [n for n in ref.namelist() if n.startswith('BOOT-INF/classes/') and n.endswith('.class')]
        report['jar_inventory'] = {}
        for name, path in jars.items():
            with zipfile.ZipFile(path) as z:
                differences = [n for n in app_classes if n not in z.namelist() or z.read(n) != ref.read(n)]
                assert not differences, (name, differences)
                report['jar_inventory'][name] = {'path': str(path), 'sha256': sha(path), 'identical_application_classes': len(app_classes)}
                if name == 'baseline':
                    assert set(z.namelist()) == set(ref.namelist())
                    changed = [n for n in ref.namelist() if ref.read(n) != z.read(n)]
                    assert changed == ['BOOT-INF/classes/application.yml'], changed
        check('packaged application classes match; baseline changes configuration only', classes=len(app_classes))
        assert 'BOOT-INF/classes/com/coinmind/ai/provider/GeminiRecoveryState.class' in app_classes
        assert 'BOOT-INF/classes/com/coinmind/manifold/service/ManifoldIntelligenceService.class' in app_classes
        for kind, path in jars.items():
            with zipfile.ZipFile(path) as z:
                for n in z.namelist():
                    if n.startswith('BOOT-INF/classes/'):
                        assert not any(token in (n.encode() + z.read(n)).lower()
                                       for token in (b'polymarket', b'kalshi', b'finfeedapi')), (kind, n)
            for f in (path.parent / 'frontend').rglob('*'):
                if f.is_file():
                    assert not any(token in f.read_bytes().lower()
                                   for token in (b'polymarket', b'kalshi', b'finfeedapi')), (kind, f)
        check('Manifold and recovery classes retained; retired providers absent from application and frontend')
        # Extract copies solely for same-package, network-free persistence probes.
        probe_cps = {}
        for kind in ('baseline', 'candidate'):
            extract = artifact / (kind + '-classes')
            extract.mkdir()
            with zipfile.ZipFile(jars[kind]) as z:
                for n in z.namelist():
                    if n.startswith('BOOT-INF/classes/') and not n.endswith('/'):
                        f = extract / n.removeprefix('BOOT-INF/classes/')
                        f.parent.mkdir(parents=True, exist_ok=True)
                        f.write_bytes(z.read(n))
            java = artifact / (kind + '-probe') / 'BaselineLedgerProbe.java'
            java.parent.mkdir()
            java.write_text(PROBE)
            subprocess.run(['javac', '-cp', str(extract), '-d', str(java.parent), str(java)], check=True, env=env, cwd=artifact)
            probe_cps[kind] = str(java.parent) + os.pathsep + str(extract)

        def probe(kind, ledger, action, offset=0):
            output = subprocess.check_output(['java', '-cp', probe_cps[kind], 'com.coinmind.ai.provider.BaselineLedgerProbe',
                                             str(ledger), (now + dt.timedelta(seconds=offset)).isoformat(), action],
                                            cwd=artifact, env=env, stderr=subprocess.STDOUT, text=True)
            assert 'PASS ' + action in output

        class MarketMock(e2e.Mock):
            posts = 0

            def do_POST(self):
                type(self).posts += 1
                self.respond(500, {'error': 'unexpected request: all AI must be disabled'})

            def do_GET(self):
                if self.path.startswith('/api/v3/klines'):
                    query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
                    step = {'1m': 60, '5m': 300, '15m': 900, '1h': 3600, '4h': 14400, '1d': 86400}.get(query.get('interval', ['1m'])[0], 60)
                    end = int(time.time()) // step * step
                    rows = [[(end-(250-i)*step)*1000, str(69000+i*4), str(69010+i*4), str(68990+i*4),
                             str(69001+i*4), '10', (end-(249-i)*step)*1000-1, '700000', 100] for i in range(250)]
                    self.respond(200, rows)
                else:
                    super().do_GET()

        server = e2e.http.server.ThreadingHTTPServer(('127.0.0.1', 0), MarketMock)
        server.daemon_threads = True
        threading.Thread(target=server.serve_forever, daemon=True).start()
        mock_port = server.server_address[1]

        def boot(kind, ledger, name, failure=False):
            port = e2e.port()
            log = artifact / (name + '.log')
            options = ['--server.address=127.0.0.1', '--server.port=' + str(port),
                       '--spring.config.location=classpath:/application.yml',
                       '--spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:1/unused',
                       '--spring.r2dbc.username=unused', '--spring.r2dbc.password=unused',
                       '--spring.sql.init.mode=never', '--management.health.r2dbc.enabled=false',
                       '--coinmind.persistence.enabled=false', '--coinmind.ai.required-for-entry=false',
                       '--coinmind.billing.enabled=false', '--coinmind.push.enabled=false',
                       '--coinmind.news.enabled=false', '--coinmind.news.retention-enabled=false',
                       '--coinmind.futures.paper-replay-archive-enabled=false',
                       '--coinmind.futures.offline-replay-job-enabled=false', '--coinmind.futures.market-data-enabled=false',
                       '--coinmind.market.binance-rest-base-url=http://127.0.0.1:' + str(mock_port),
                       '--coinmind.market.binance-websocket-base-url=ws://127.0.0.1:' + str(mock_port),
                       '--coinmind.futures.base-url=http://127.0.0.1:' + str(mock_port),
                       '--coinmind.manifold.base-url=http://127.0.0.1:' + str(mock_port)]
            if kind == 'candidate':
                options += ['--coinmind.ai.enabled=false', '--coinmind.ai.provider=gemini',
                            '--coinmind.ai.gemini.news-enabled=false', '--coinmind.ai.automatic-enabled=false',
                            '--coinmind.ai.api-key=', '--coinmind.ai.gemini.api-key=']
            child_env = dict(env)
            if kind == 'baseline':
                # Prove legacy .env variable NAMES cannot override embedded baseline policy.
                child_env.update(AI_ENABLED='true', AI_PROVIDER='openai', GEMINI_NEWS_ENABLED='true',
                                 AI_AUTOMATIC_ENABLED='true', GEMINI_API_KEY='synthetic-not-a-credential', AI_API_KEY='synthetic-not-a-credential')
            with log.open('w') as out:
                proc = subprocess.Popen(['java', '-XX:ActiveProcessorCount=2', '-Xmx384m',
                                         '-Dcoinmind.gemini.state-dir=' + str(ledger), '-jar', str(jars[kind]), *options],
                                        cwd=artifact, env=child_env, stdout=out, stderr=subprocess.STDOUT)
            processes.append(proc)
            if failure:
                proc.wait(timeout=60)
                assert proc.returncode != 0 and 'Gemini durable ledger unavailable' in log.read_text(), str(log)
                check('startup refused unsafe ledger', process=name)
                return None, proc
            base = 'http://127.0.0.1:' + str(port)

            def ready():
                assert proc.poll() is None, str(log)
                return e2e.request(base, '/actuator/health').get('status') == 'UP'

            e2e.wait_for(ready, timeout=70)
            status = e2e.request(base, '/api/v1/ai/status')
            assert status['state'] == 'DISABLED' and status['provider'] == 'gemini' and not status['enabled'], status
            assert not status['apiKeyConfigured'], status
            return (base, status), proc

        def functional(base, label):
            ticker = e2e.wait_for(lambda: e2e.request(base, '/api/v1/market/tickers/BTCUSDT'))
            history = e2e.wait_for(lambda: e2e.request(base, '/api/v1/market/history/BTCUSDT/15m'))
            advisor = e2e.request(base, '/api/v1/trade/setup/BTCUSDT/15m')
            futures = e2e.request(base, '/api/v1/futures/readiness')
            assert ticker['symbol'] == 'BTCUSDT' and len(history) >= 250
            assert advisor['symbol'] == 'BTCUSDT' and advisor.get('action'), advisor
            assert futures['status'] == 'BLOCKED' and not futures['liveOrdersEnabled'] and not futures['futuresSignalsEnabled']
            for path in ('/api/v1/polymarket/BTCUSDT', '/api/v1/ai/polymarket-intelligence/BTCUSDT',
                         '/api/v1/kalshi/BTCUSDT', '/api/v1/finfeedapi/BTCUSDT'):
                try:
                    e2e.request(base, path)
                    raise AssertionError('retired provider endpoint exists: ' + path)
                except urllib.error.HTTPError as error:
                    assert error.code == 404, (path, error.code)
            source_health = e2e.request(base, '/api/v1/manifold/source-health')
            assert [s['source'] for s in source_health['sources']] == ['MANIFOLD']
            (artifact / (label + '-http.json')).write_text(json.dumps({'ticker': ticker, 'advisor': advisor, 'futures': futures}, indent=2))
            check('market/advisor available; Futures orders BLOCKED', process=label, action=advisor['action'], candles=len(history))

        for scenario, expected in [('open', 'OPEN'), ('auth', 'AUTH_ERROR'), ('quota', 'CLOSED'), ('half', 'OPEN')]:
            ledger = artifact / ('ledger-' + scenario)
            probe('candidate', ledger, 'seed-' + scenario)
            marker = ledger / 'operator-evidence.keep'
            marker.write_bytes(b'synthetic evidence; must survive every startup')
            before = {f.name: f.read_bytes() for f in ledger.iterdir()}
            for index, kind in enumerate(('baseline', 'candidate', 'baseline')):
                label = scenario + '-' + str(index) + '-' + kind
                result, proc = boot(kind, ledger, label)
                base, status = result
                assert status['recovery']['state'] == expected, status
                if scenario == 'open' and index == 0:
                    functional(base, label)
                    boot('candidate', ledger, 'candidate-lock-refusal', failure=True)
                if scenario == 'open' and index == 1:
                    functional(base, label)
                    boot('baseline', ledger, 'baseline-lock-refusal', failure=True)
                stop(proc)
                assert proc.returncode in (0, 143), (label, proc.returncode)
                assert {f.name: f.read_bytes() for f in ledger.iterdir()} == before, label
                probe(kind, ledger, scenario)
                check('start/stop preserves ledger byte-for-byte and admission gates', process=label, recovery=expected)
            # Prove candidate writes can be consumed after rollback, using no network.
            if scenario == 'open':
                probe('candidate', ledger, 'recover', offset=181)
                probe('baseline', ledger, 'recovered', offset=181)
                result, proc = boot('baseline', ledger, 'validated-recovery-rollback')
                assert result[1]['recovery']['state'] == 'CLOSED'
                stop(proc)
                probe('baseline', ledger, 'recovered', offset=181)
                check('candidate validated recovery survives baseline rollback with both attempts retained')

        # Corrupt existing storage must never be silently initialized by either binary.
        corrupt = artifact / 'ledger-corrupt'
        corrupt.mkdir()
        (corrupt / 'gemini.properties').write_bytes(b'circuit=AUTH_ERROR\ncalls=\n')
        for kind in ('baseline', 'candidate'):
            before = (corrupt / 'gemini.properties').read_bytes()
            boot(kind, corrupt, kind + '-corruption-refusal', failure=True)
            assert (corrupt / 'gemini.properties').read_bytes() == before
        assert MarketMock.posts == 0
        check('zero mock POSTs; no live credentials inherited; baseline legacy enable variables overridden', mock_posts=MarketMock.posts)
        assert {n: sha(candidate / n) for n in original_hashes} == original_hashes
        verify(candidate)
        verify(baseline)
        check('original candidate unchanged; baseline and candidate offline hashes verified')
        # Seal only this newly created staging bundle, never imported releases.
        for f in baseline.rglob('*'):
            if f.is_file():
                f.chmod(0o444)
        for directory in sorted((f for f in baseline.rglob('*') if f.is_dir()), reverse=True):
            directory.chmod(0o555)
        baseline.chmod(0o555)
        verify(baseline)
        check('new staging bundle sealed read-only and manifest reverified')
        report['baseline'] = str(baseline)
        report['baseline_manifest_sha256'] = sha(baseline / 'manifest.json')
        report['status'] = 'READY_FOR_OPERATOR_REVIEW'
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        for proc in processes:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=10)
        if server:
            e2e.Mock.stop.set()
            server.shutdown()
            server.server_close()
        report['all_child_processes_stopped'] = all(p.poll() is not None for p in processes)
        (artifact / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('RESULT', report['status'], flush=True)


if __name__ == '__main__':
    main()
