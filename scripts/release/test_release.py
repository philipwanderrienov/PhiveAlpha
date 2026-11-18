"""No services/network: artifact integrity and activation fault injection."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('activate', Path(__file__).with_name('activate.py'))
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)

class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.releases = self.base / 'releases'
        self.releases.mkdir()
        self.old = self.bundle('old')
        self.new = self.bundle('new')
        (self.base / 'current').symlink_to(self.old)

    def bundle(self, name):
        r = self.releases / name
        (r / 'frontend').mkdir(parents=True)
        (r / 'backend.jar').write_bytes(name.encode())
        (r / 'frontend/index.html').write_bytes(name.encode())
        files = {str(f.relative_to(r)): hashlib.sha256(f.read_bytes()).hexdigest() for f in r.rglob('*') if f.is_file()}
        (r / 'manifest.json').write_text(json.dumps({'schema': 1, 'ledger_protocol': 'phase15-v1', 'files': files}))
        return r

    def test_verify_and_tamper(self):
        a.verify(self.new)
        (self.new / 'backend.jar').write_bytes(b'bad')
        with self.assertRaises(RuntimeError):
            a.verify(self.new)

    def test_extra_file(self):
        (self.new / '.env').write_text('dummy')
        with self.assertRaises(RuntimeError):
            a.verify(self.new)

    def test_symlink(self):
        (self.new / 'link').symlink_to(self.old)
        with self.assertRaises(RuntimeError):
            a.verify(self.new)

    def test_default_no_mutation(self):
        with patch('sys.argv', ['activate.py', str(self.new)]), patch.object(a, 'command') as cmd, contextlib.redirect_stdout(io.StringIO()):
            a.main()
            cmd.assert_not_called()
        self.assertEqual((self.base / 'current').resolve(), self.old)

    def test_failed_health_restores_pair_without_touching_ledger(self):
        ledger = self.base / 'ledger'
        ledger.mkdir()
        (ledger / 'gemini.properties').write_bytes(b'preserve all state')
        att = {k: True for k in ('single_project_key_owner', 'ledger_migration_verified', 'rollback_compatible', 'config_templates_installed', 'advisor_only_manifold_only')}
        (self.base / 'operator-attestation.json').write_text(json.dumps(att))
        start = '-Dcoinmind.gemini.state-dir=/var/lib/coinmind/ai-recovery /opt/coinmind/current/backend.jar'
        with patch('sys.argv', ['activate.py', str(self.new), '--authorize', a.ACK]), patch.object(a, 'BASE', self.base), patch.object(a, 'LEDGER', ledger), patch.object(a.os, 'geteuid', return_value=0), patch.object(a, 'protected'), patch.object(a, 'command') as cmd, patch.object(a.subprocess, 'check_output', side_effect=[start, '0', '0']), patch.object(a, 'health', side_effect=[None, RuntimeError('injected'), None]):
            with self.assertRaisesRegex(RuntimeError, 'previous backend/frontend restored'):
                a.main()
        self.assertEqual((self.base / 'current').resolve(), self.old)
        self.assertEqual((ledger / 'gemini.properties').read_bytes(), b'preserve all state')
        self.assertEqual([c.args[1] for c in cmd.call_args_list if c.args[0] == 'systemctl'], ['stop', 'start', 'stop', 'start'])

if __name__ == '__main__':
    unittest.main()
