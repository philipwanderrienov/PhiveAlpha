#!/usr/bin/env python3
"""Explicit first-time bootstrap only; never imported by deployment/runtime tooling."""
import argparse
import datetime as dt
import fcntl
import hashlib
import os
from pathlib import Path
import stat

ACK = 'ACKNOWLEDGE_ALL_PROJECT_KEY_CLIENTS_QUIET_24H'
INITIAL = (b'next=1970-01-01T00:00:00Z\ncircuit=CLOSED\nreason=UNVERIFIED\n'
           b'failures=0\nsuccess=\nfailure=\ncalls=\n')


def cutoff_utc(value):
    if not value.endswith('Z'):
        raise ValueError('cutoff must be explicit UTC ending in Z')
    return dt.datetime.fromisoformat(value[:-1] + '+00:00')


def bootstrap(directory, cutoff, evidence, acknowledgment, all_clients_stopped,
              no_unresolved_restrictions):
    # Validate attestations/evidence before any target mutation. Never offer a clock override.
    now = dt.datetime.now(dt.timezone.utc)
    last = cutoff_utc(cutoff)
    if now - last < dt.timedelta(hours=24):
        raise ValueError('a minimum completed 24-hour quiet period is required')
    if acknowledgment != ACK or not all_clients_stopped or not no_unresolved_restrictions:
        raise ValueError('global containment, quiet-period and restriction acknowledgments required')
    evidence_bytes = Path(evidence).read_bytes()
    if not evidence_bytes.strip():
        raise ValueError('nonempty operator cutoff evidence required')
    directory = Path(directory)
    if not directory.is_absolute() or '..' in directory.parts:
        raise ValueError('absolute canonical directory required')
    # Reject symlinks at every level; operator must provision the final directory privately.
    for path in [*reversed(directory.parents), directory]:
        s = path.lstat()
        if not stat.S_ISDIR(s.st_mode) or (s.st_mode & 0o022 and s.st_mode & 0o1000 == 0):
            raise ValueError('real directories with trusted non-writable ancestors required')
    dfd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        s = os.fstat(dfd)
        if s.st_uid != os.geteuid() or stat.S_IMODE(s.st_mode) != 0o700:
            raise ValueError('target must be owned by the executing ledger owner with mode 0700')
        names = set(os.listdir(dfd))
        if names - {'gemini.lock'}:
            raise ValueError('existing accounting, evidence or interrupted initialization; investigate without resetting')
        lfd = os.open('gemini.lock', os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600, dir_fd=dfd)
        try:
            s = os.fstat(lfd)
            if not stat.S_ISREG(s.st_mode) or s.st_nlink != 1 or s.st_size != 0 or s.st_uid != os.geteuid() or stat.S_IMODE(s.st_mode) != 0o600:
                raise ValueError('lock must be an empty private owner-controlled regular file')
            # Java FileChannel.tryLock uses POSIX record locks, NOT BSD flock.
            fcntl.lockf(lfd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if set(os.listdir(dfd)) - {'gemini.lock'}:
                raise ValueError('target changed or already initialized')
            # Fixed exclusive temp name intentionally remains on interruption: fail closed on retry.
            fd = os.open('gemini.bootstrap.pending', os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=dfd)
            with os.fdopen(fd, 'wb') as out:
                out.write(('# First-time bootstrap; no historical usage reconstructed\n'
                           '# cutoff_utc=' + last.isoformat() + '\n'
                           '# initialized_utc=' + now.isoformat() + '\n'
                           '# evidence_sha256=' + hashlib.sha256(evidence_bytes).hexdigest() + '\n').encode('ascii'))
                out.write(INITIAL)
                out.flush()
                os.fsync(out.fileno())
            os.fsync(dfd)
            # Same-directory hard-link publication is atomic and fails if destination exists.
            os.link('gemini.bootstrap.pending', 'gemini.properties', src_dir_fd=dfd, dst_dir_fd=dfd, follow_symlinks=False)
            os.fsync(dfd)
            os.unlink('gemini.bootstrap.pending', dir_fd=dfd)
            os.fsync(dfd)
        finally:
            os.close(lfd)
    finally:
        os.close(dfd)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--state-dir', type=Path, required=True)
    p.add_argument('--last-request-cutoff-utc', required=True)
    p.add_argument('--cutoff-evidence-file', type=Path, required=True)
    p.add_argument('--acknowledge', choices=[ACK], required=True)
    p.add_argument('--all-project-key-clients-stopped', action='store_true', required=True)
    p.add_argument('--no-unresolved-auth-quota-or-cooldown-restrictions', action='store_true', required=True)
    a = p.parse_args()
    bootstrap(a.state_dir, a.last_request_cutoff_utc, a.cutoff_evidence_file,
              a.acknowledge, a.all_project_key_clients_stopped,
              a.no_unresolved_auth_quota_or_cooldown_restrictions)
    print('Fresh UNVERIFIED ledger durably initialized. No Gemini request or activation performed.')


if __name__ == '__main__':
    main()
