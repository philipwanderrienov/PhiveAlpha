#!/usr/bin/env python3
"""Offline bundle verification by default; production mutation requires authorization."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import time
import urllib.request

BASE = Path('/opt/coinmind')
LEDGER = Path('/var/lib/coinmind/ai-recovery')
SERVICE = 'coinmind-backend.service'
ACK = 'AUTHORIZE_PRODUCTION_SWITCH'

def verify(path):
    if path.is_symlink() or not path.is_dir():
        raise RuntimeError('release must be a real directory')
    manifest = json.loads((path / 'manifest.json').read_text())
    if manifest['schema'] != 1 or manifest['ledger_protocol'] != 'phase15-v1':
        raise RuntimeError('unsupported ledger/release protocol')
    files = manifest['files']
    if 'backend.jar' not in files or 'frontend/index.html' not in files:
        raise RuntimeError('incomplete release')
    actual = set()
    for f in path.rglob('*'):
        if f.is_symlink() or not (f.is_dir() or f.is_file()):
            raise RuntimeError('links/special files forbidden in release')
        if f.is_file() and f != path / 'manifest.json':
            actual.add(str(f.relative_to(path)))
    if actual != set(files):
        raise RuntimeError('unexpected or missing release files')
    for name, expected in files.items():
        f = path / name
        if not f.resolve().is_relative_to(path.resolve()) or hashlib.sha256(f.read_bytes()).hexdigest() != expected:
            raise RuntimeError('release checksum mismatch')
    return manifest

def command(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def health():
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for _ in range(45):
        try:
            command('systemctl', 'is-active', '--quiet', SERVICE)
            with opener.open('http://127.0.0.1:8080/actuator/health', timeout=2) as r:
                if json.load(r).get('status') == 'UP':
                    return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('backend did not become healthy')

def switch(target):
    temp = BASE / 'current.next'
    if temp.exists() or temp.is_symlink():
        raise RuntimeError('stale current.next; operator investigation required')
    temp.symlink_to(target)
    temp.replace(BASE / 'current')

def protected(path):
    s = path.lstat()
    if s.st_uid != 0 or s.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
        raise RuntimeError('root-owned, non-group/world-writable production files required')

def stopped():
    command('systemctl', 'stop', SERVICE)
    pid = subprocess.check_output(['systemctl', 'show', SERVICE, '-p', 'MainPID', '--value'], text=True).strip()
    if pid != '0':
        raise RuntimeError('backend did not stop; refusing switch')

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('release', type=Path)
    p.add_argument('--authorize', choices=[ACK])
    a = p.parse_args()
    target = a.release.absolute()
    verify(target)
    if not a.authorize:
        print('DRY RUN: bundle hashes/protocol verified. No service, config, ledger or production file mutation. Activation prerequisites not certified.')
        return
    if os.geteuid() != 0 or target.parent != BASE / 'releases':
        p.error('authorized activation requires root and /opt/coinmind/releases/<id>')
    protected(BASE)
    protected(BASE / 'releases')
    with (BASE / 'activation.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        current = BASE / 'current'
        if not current.is_symlink():
            raise RuntimeError('operator must complete baseline migration first')
        old = current.resolve(strict=True)
        if old.parent != BASE / 'releases' or old == target:
            raise RuntimeError('invalid baseline or already active')
        for release in (old, target):
            verify(release)
            for f in [release, *release.rglob('*')]:
                protected(f)
        protected(BASE / 'operator-attestation.json')
        attestation = json.loads((BASE / 'operator-attestation.json').read_text())
        if attestation != {'single_project_key_owner': True, 'ledger_migration_verified': True,
                           'rollback_compatible': True, 'config_templates_installed': True,
                           'advisor_only_manifold_only': True}:
            raise RuntimeError('operator prerequisite attestations missing')
        if not (LEDGER / 'gemini.properties').is_file():
            raise RuntimeError('persistent ledger missing; never initialize/reset during deployment')
        # Inspect only necessary unit fields; never print environment or credentials.
        start = subprocess.check_output(['systemctl', 'show', SERVICE, '-p', 'ExecStart', '--value'], text=True)
        if '-Dcoinmind.gemini.state-dir=/var/lib/coinmind/ai-recovery' not in start or '/opt/coinmind/current/backend.jar' not in start:
            raise RuntimeError('reviewed service paths not installed')
        command('nginx', '-t')
        health()
        record = BASE / ('activation-' + str(time.time_ns()) + '.json')
        record.write_text(json.dumps({'previous': str(old), 'requested': str(target), 'status': 'pending'}) + '\n')
        # Stop before changing shared pointer: never start a second Gemini JVM.
        # A stop failure leaves the pointer unchanged and requires operator action.
        stopped()
        try:
            switch(target)
            command('systemctl', 'start', SERVICE)
            health()
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            with opener.open('http://127.0.0.1/', timeout=5) as r:
                if hashlib.sha256(r.read()).hexdigest() != hashlib.sha256((target / 'frontend/index.html').read_bytes()).hexdigest():
                    raise RuntimeError('Nginx is not serving selected release')
            record.write_text(json.dumps({'previous': str(old), 'requested': str(target), 'status': 'healthy'}) + '\n')
            print('Activated; perform operator UI/API acceptance. Previous release retained:', old)
        except BaseException:
            # Stop new process BEFORE restoring pointer. Never restore ledger snapshots.
            stopped()
            if current.resolve() != old:
                switch(old)
            command('systemctl', 'start', SERVICE)
            health()
            record.write_text(json.dumps({'previous': str(old), 'requested': str(target), 'status': 'rolled_back'}) + '\n')
            raise RuntimeError('activation failed; previous backend/frontend restored; ledger retained') from None

if __name__ == '__main__':
    main()
