"""Synthetic private directories only; no production state or network."""
import datetime as dt
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('bootstrap', Path(__file__).with_name('bootstrap_gemini_ledger.py'))
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)


class BootstrapTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.state = self.root / 'state'
        self.state.mkdir(mode=0o700)
        self.evidence = self.root / 'evidence'
        self.evidence.write_text('Synthetic containment record only')
        self.cutoff = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=25)).isoformat().replace('+00:00', 'Z')

    def run_bootstrap(self, **kwargs):
        args = dict(directory=self.state, cutoff=self.cutoff, evidence=self.evidence,
                    acknowledgment=b.ACK, all_clients_stopped=True, no_unresolved_restrictions=True)
        args.update(kwargs)
        b.bootstrap(**args)

    def test_quiet_period_and_attestations(self):
        for kwargs in ({'cutoff': dt.datetime.now(dt.timezone.utc).isoformat().replace('+00:00', 'Z')},
                       {'cutoff': '2099-01-01T00:00:00Z'}, {'cutoff': '2020-01-01T00:00:00+00:00'},
                       {'all_clients_stopped': False}, {'acknowledgment': ''},
                       {'no_unresolved_restrictions': False}):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                self.run_bootstrap(**kwargs)
        self.assertEqual(list(self.state.iterdir()), [])

    def test_existing_valid_corrupt_empty_and_pending_refused(self):
        for name, data in [('gemini.properties', b.INITIAL), ('gemini.properties', b'corrupt'),
                           ('gemini.properties', b''), ('gemini.tmp', b'prior accounting'),
                           ('gemini.bootstrap.pending', b'partial')]:
            path = self.state / name
            path.write_bytes(data)
            with self.subTest(name=name, data=data), self.assertRaises(ValueError):
                self.run_bootstrap()
            self.assertEqual(path.read_bytes(), data)
            path.unlink()

    def test_java_lock_contention(self):
        # Java owns the actual FileChannel lock in another process.
        probe = self.root / 'Lock.java'
        probe.write_text('''import java.nio.channels.*; import java.nio.file.*;
class Lock { public static void main(String[] a) throws Exception {
 try(var c=FileChannel.open(Path.of(a[0]),StandardOpenOption.WRITE);var l=c.lock()) {
 System.out.println("locked");System.out.flush();System.in.read(); } } }''')
        lock = self.state / 'gemini.lock'
        lock.touch(mode=0o600)
        proc = subprocess.Popen(['java', str(probe), str(lock)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        try:
            self.assertEqual(proc.stdout.readline().strip(), 'locked')
            with self.assertRaises(BlockingIOError):
                self.run_bootstrap()
            self.assertFalse((self.state / 'gemini.properties').exists())
        finally:
            proc.communicate('\n', timeout=20)

    def test_interrupted_initialization_fails_closed(self):
        with patch.object(b.os, 'link', side_effect=InterruptedError('synthetic interruption')):
            with self.assertRaises(InterruptedError):
                self.run_bootstrap()
        self.assertFalse((self.state / 'gemini.properties').exists())
        with self.assertRaises(ValueError):
            self.run_bootstrap()

    def test_publication_does_not_overwrite_racing_destination(self):
        link = os.link
        def race(*args, **kwargs):
            (self.state / 'gemini.properties').write_bytes(b'preserve')
            return link(*args, **kwargs)
        with patch.object(b.os, 'link', side_effect=race), self.assertRaises(FileExistsError):
            self.run_bootstrap()
        self.assertEqual((self.state / 'gemini.properties').read_bytes(), b'preserve')

    def test_fresh_schema_loads_in_actual_java_state(self):
        self.run_bootstrap()
        ledger = self.state / 'gemini.properties'
        self.assertTrue(ledger.read_bytes().endswith(b.INITIAL))
        for path in (ledger, self.state / 'gemini.lock'):
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        probe = self.root / 'Probe.java'
        probe.write_text('''import com.coinmind.ai.provider.GeminiRecoveryState;
import java.nio.file.*; import java.time.*;
class Probe { public static void main(String[] a) throws Exception {
 try(var s=new GeminiRecoveryState(Clock.systemUTC(),Path.of(a[0]))) {
 var m=s.snapshot(); if(!m.get("state").equals("CLOSED") ||
 !m.get("reasonCode").equals("UNVERIFIED") || m.get("lastSuccess")!=null ||
 m.get("lastFailure")!=null) throw new AssertionError(m); } } }''')
        source = Path(__file__).resolve().parents[2] / 'backend/src/main/java/com/coinmind/ai/provider/GeminiRecoveryState.java'
        subprocess.run(['javac', '-d', str(self.root), str(source), str(probe)], check=True, capture_output=True)
        before = ledger.read_bytes()
        subprocess.run(['java', '-cp', str(self.root), 'Probe', str(self.state)], check=True, capture_output=True)
        self.assertEqual(ledger.read_bytes(), before)
        with self.assertRaises(ValueError):
            self.run_bootstrap()

    def test_permissions_and_symlinks(self):
        self.state.chmod(0o755)
        with self.assertRaises(ValueError): self.run_bootstrap()
        self.state.chmod(0o700)
        link = self.root / 'link'
        link.symlink_to(self.state)
        with self.assertRaises(ValueError): self.run_bootstrap(directory=link)
