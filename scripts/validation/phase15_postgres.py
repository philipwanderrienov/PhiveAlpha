#!/usr/bin/env python3
"""Run archive integration tests on a newly initialized, disposable loopback cluster."""
import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PG = Path("/usr/lib/postgresql/17/bin")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--confirm", required=True, choices=["ISOLATED_TEST_DATABASE_ONLY"])
    args = parser.parse_args()
    work = Path(tempfile.mkdtemp(prefix="coinmind-phase15-postgres-"))
    data = work / "cluster"
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
    assert port not in (5432, 8080)
    env = {k: v for k, v in os.environ.items() if k in ("PATH", "HOME", "JAVA_HOME", "LANG", "LC_ALL")}
    env.update(PGHOST="127.0.0.1", PGPORT=str(port), PGUSER="coinmind_e2e", PGPASSWORD="disposable-test-password")
    password = work / "test-password"
    password.write_text(env["PGPASSWORD"])
    password.chmod(0o600)
    started = False
    print("ARTIFACTS", work, flush=True)
    report = {"directory": str(work), "host": "127.0.0.1", "port": port, "database": "coinmind_replay_test"}
    with (work / "commands.log").open("w") as log:
        def run(command):
            subprocess.run([str(x) for x in command], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        try:
            run([PG / "initdb", "-D", data, "--username=coinmind_e2e", "--pwfile="+str(password),
                 "--auth-host=scram-sha-256", "--auth-local=trust", "--no-locale", "--encoding=UTF8"])
            run([PG / "pg_ctl", "-D", data, "-l", work / "postgres.log", "-o", f"-h 127.0.0.1 -p {port} -k {work}", "-w", "start"])
            started = True
            run([PG / "createdb", "coinmind_replay_test"])
            for schema in ("futures_paper_replay_archive.sql", "futures_paper_replay_equity.sql", "futures_paper_replay_precision_migration.sql"):
                run([PG / "psql", "-X", "-v", "ON_ERROR_STOP=1", "-d", "coinmind_replay_test", "-f", ROOT / "backend/src/main/resources/db" / schema])
            env.update(FUTURES_ARCHIVE_TEST_CONFIRM=args.confirm,
                       FUTURES_ARCHIVE_TEST_R2DBC_URL=f"r2dbc:postgresql://127.0.0.1:{port}/coinmind_replay_test",
                       FUTURES_ARCHIVE_TEST_DB_USER="coinmind_e2e", FUTURES_ARCHIVE_TEST_DB_PASSWORD=env["PGPASSWORD"])
            run(["mvn", "-f", "backend/pom.xml", "-Dtest=FuturesPaperReplayPostgresIT,FuturesPaperReplayTransactionIT,FuturesOfflineCsvToPostgresIT,FuturesPaperReplayServiceIT", "test"])
            report["result"] = "PASS"
        finally:
            if started:
                run([PG / "pg_ctl", "-D", data, "-m", "fast", "-w", "stop"])
            report["clusterStopped"] = not (data / "postmaster.pid").exists()
            (work / "report.json").write_text(json.dumps(report, indent=2))
            print("REPORT", work / "report.json", flush=True)
            # Artifacts deliberately retained; no recursive deletion.


if __name__ == "__main__":
    main()
