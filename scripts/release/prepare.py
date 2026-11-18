#!/usr/bin/env python3
"""Build an isolated source snapshot. Default is read-only planning."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]

def run(args, cwd=ROOT):
    subprocess.run(args, cwd=cwd, check=True)

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stage', type=Path, help='Explicit NEW bundle path outside repository and production; otherwise dry-run')
    a = p.parse_args()
    branch = subprocess.check_output(['git', 'branch', '--show-current'], cwd=ROOT, text=True).strip()
    if branch != 'main':
        p.error('main required')
    print('Plan: isolated source copy -> Maven verify -> Angular production build -> status tests -> hash manifest. No activation.')
    if not a.stage:
        return
    dest = a.stage.absolute()
    if dest.exists() or dest.is_symlink() or not dest.is_relative_to(Path('/tmp')):
        p.error('stage must be a new /tmp path; production import is a separate operator action')
    # Never read .env, data, target or generated artifacts into the build copy.
    with tempfile.TemporaryDirectory(prefix='coinmind-release-build-') as temp:
        source = Path(temp) / 'source'
        shutil.copytree(ROOT, source, ignore=shutil.ignore_patterns('.git', '.env*', 'node_modules', 'target', 'dist', 'data', '__pycache__', '.aws', '.codex', '.agents'))
        inventory = {str(f.relative_to(source)): digest(f) for f in sorted(source.rglob('*')) if f.is_file()}
        # verify (no clean) in a fresh copy, so the running target JAR is untouched.
        run(['mvn', '-o', '-f', 'backend/pom.xml', 'verify'], source)
        # Use installed dependencies without mutating lockfiles or downloading packages.
        if not (ROOT / 'frontend/node_modules').is_dir():
            raise RuntimeError('Install reviewed lockfile dependencies separately before staging')
        (source / 'frontend/node_modules').symlink_to(ROOT / 'frontend/node_modules', target_is_directory=True)
        run(['npm', 'run', 'build', '--', '--configuration', 'production'], source / 'frontend')
        run(['npm', 'run', 'test:ai-status'], source / 'frontend')
        jar = source / 'backend/target/coinmind-backend-0.1.0-SNAPSHOT.jar'
        front = source / 'frontend/dist/coinmind-web/browser'
        if not jar.is_file() or not (front / 'index.html').is_file():
            raise RuntimeError('Missing build outputs')
        dest.mkdir()  # failed/incomplete stages are kept for investigation, never activated
        shutil.copy2(jar, dest / 'backend.jar')
        shutil.copytree(front, dest / 'frontend')
        files = {str(f.relative_to(dest)): digest(f) for f in sorted(dest.rglob('*')) if f.is_file()}
        manifest = {'schema': 1, 'main': subprocess.check_output(['git', 'rev-parse', 'main'], cwd=ROOT, text=True).strip(),
                    'dirty_snapshot': True, 'source_sha256': inventory, 'files': files,
                    'checks': ['maven verify', 'angular production', 'ai status'], 'ledger_protocol': 'phase15-v1'}
        (dest / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        print(f'Prepared {dest}; no production changes. Review source inventory and checks before root-owned import.')

if __name__ == '__main__':
    main()
