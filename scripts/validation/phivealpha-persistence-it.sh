#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
pg_bin=${PHIVEALPHA_TEST_PG_BIN:-/usr/lib/postgresql/17/bin}
if [[ $EUID == 0 ]]; then
    echo "Run as an unprivileged user." >&2
    exit 1
fi

# Probe support for loopback before creating any database files.
port=$(python3 - <<'PY'
import socket
with socket.socket() as listener:
    listener.bind(("127.0.0.1", 0))
    print(listener.getsockname()[1])
PY
)

sandbox_dir=$(mktemp -d /tmp/phivealpha-it.XXXXXXXX)
cleanup() {
    if [[ -f "$sandbox_dir/data/postmaster.pid" ]]; then
        "$pg_bin/pg_ctl" -D "$sandbox_dir/data" -m fast -w stop || return 1
    fi
    rm -rf -- "$sandbox_dir"
}
trap cleanup EXIT
umask 077
mkdir "$sandbox_dir/socket"
password=$(python3 -c 'import secrets; print(secrets.token_hex(32))')
printf '%s\n' "$password" > "$sandbox_dir/password"
"$pg_bin/initdb" -D "$sandbox_dir/data" --username=phivealpha_it --pwfile="$sandbox_dir/password" \
    --auth-local=trust --auth-host=scram-sha-256 --no-locale --encoding=UTF8
rm -- "$sandbox_dir/password"
"$pg_bin/pg_ctl" -D "$sandbox_dir/data" -l "$sandbox_dir/postgres.log" \
    -o "-h 127.0.0.1 -p $port -k $sandbox_dir/socket" -w start

# All administrative SQL uses the private Unix socket of the cluster created above.
unset PGHOST PGHOSTADDR PGPORT PGDATABASE PGUSER PGPASSWORD PGSERVICE PGSERVICEFILE PGOPTIONS
psql=("$pg_bin/psql" -X --host="$sandbox_dir/socket" --port="$port" --username=phivealpha_it -v ON_ERROR_STOP=1)
"${psql[@]}" --dbname=postgres -c 'CREATE DATABASE phivealpha_it'
"${psql[@]}" --dbname=phivealpha_it -f "$repo_dir/backend/src/main/resources/phivealpha/sql/001-idx.sql"
# The test verifies the exact data directory before running any writes.
PHIVEALPHA_IT_DATA_DIR="$sandbox_dir/data" \
PHIVEALPHA_R2DBC_URL="r2dbc:postgresql://127.0.0.1:$port/phivealpha_it" \
PHIVEALPHA_R2DBC_USERNAME=phivealpha_it \
PHIVEALPHA_R2DBC_PASSWORD="$password" \
mvn -f "$repo_dir/backend/pom.xml" -Dtest=IdxPersistenceIT test "$@"
