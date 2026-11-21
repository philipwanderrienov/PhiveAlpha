# Instruksi agen PhiveAlpha

- Baca `docs/development/current-state.md`, entri terbaru `docs/development/session-log.md`, dan handoff yang relevan. Periksa `main` dan status worktree sebelum mengedit.
- Pertahankan perubahan lokal dan perilaku yang masih digunakan. Perbarui status dan log sesi setelah perubahan kode, arsitektur atau UX yang berarti. Ikuti instruksi review pengguna sebelum commit atau push.
- Validasi backend Java 25 dengan `mvn -f backend/pom.xml clean verify`. Validasi frontend dengan `npm test` dan `npm run build` di `frontend/`. Pertimbangkan tampilan desktop dan mobile ketika mengubah UI.
- Jalankan development dengan fixture dan gunakan runner PostgreSQL terisolasi untuk integration test persistence. Profil akses data eksternal memerlukan opt-in eksplisit dan mengikuti guard entitlement, kuota dan audit.
- Batas operasi Phase 1I mencakup production legacy, deployment, request provider live, serta eksekusi order. Lihat [runbook lokal](README.md) dan [arsip migrasi](docs/development/phivealpha-phase-1i.md) untuk cakupan dan rollback sumber.
