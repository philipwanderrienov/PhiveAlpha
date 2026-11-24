# PhiveAlpha Phase 1I — Legacy Cleanup & Rebranding

PhiveAlpha menjadi satu-satunya aplikasi dari build default. Maven menerbitkan `com.phivealpha:phivealpha-backend:0.1.0-SNAPSHOT` dengan entry point `com.phivealpha.PhiveAlphaApplication`. Angular dan npm memakai identitas `phivealpha`. Gunakan [runbook lokal](../../README.md) untuk mode fixture.

## Audit sebelum pemisahan

Audit memakai worktree Phase 1H di atas `main` dan ref lokal `origin/main` pada `3e96542`. Pembacaan remote langsung terhalang konfigurasi izin SSH. Handoff Phase 1A dan 1C–1H dibaca, termasuk 1D.2. Handoff khusus 1B tidak ditemukan di worktree atau riwayat Git yang tersedia.

| Area | Temuan dan batas build |
| --- | --- |
| Java runtime | Tidak ada impor atau rujukan kelas silang antarpaket di runtime. Paket IDX memakai model, layanan dan provider sendiri. Maven membatasi kompilasi ke `com/phivealpha/**` pada main dan test. |
| Java test | `IdxFoundationTest` dan `IdxPersistenceProfileTest` sebelumnya mengimpor `CoinMindApplication` untuk memeriksa isolasi. Tes fixture sekarang memeriksa ketiadaan kelas legacy di classpath. Pemeriksaan scheduler, properti legacy dan inisialisasi database tetap ada. Nama `coinmind` pada input negatif guard audit menguji penolakan target. |
| Maven dependencies | WebFlux, Actuator, Validation, Spring Data R2DBC dan driver PostgreSQL tetap tersedia. Web Push, provider kriptografi Bouncy Castle, JOSE4J, Apache HttpAsyncClient dan Jackson 2 eksplisit untuk tes CoinMind dikeluarkan dari POM. Mockito juga dikeluarkan karena tes IDX memakai fixture, mock `ExchangeFunction` atau binding WebFlux tanpa mock Mockito. |
| Konfigurasi dan data | `phivealpha.yml` dan `phivealpha/**` masuk JAR. `application.yml` legacy dan SQL paper replay di `db/` tinggal sebagai sumber pemulihan. Resource tes menggunakan fixture IDX. Port 8182, loopback, SQL init `never` dan pengaktifan profil secara eksplisit dipertahankan. |
| Angular dan npm | Sumber IDX mengimpor Angular, RxJS dan modul IDX. Target lama, aset PWA, proxy crypto dan source legacy keluar dari build. `lightweight-charts` dan `@angular/platform-browser-dynamic` keluar dari lockfile. `zone.js` dan `@angular/animations` tersisa sebagai peer opsional toolchain Angular. |
| Operasi legacy | Kelas, sumber UI dan PWA, SQL, contoh env, konfigurasi Compose, skrip release/validasi dan aset recovery tetap tersedia di repo untuk audit dan pemulihan. Skrip tersebut mengasumsikan kontrak artefak crypto dan memerlukan checkout recovery tersendiri. Workflow PhiveAlpha tidak menjalankannya. |

Checksum semua sumber Java IDX dan handoff 1H dicatat sebelum perubahan. Perubahan Java terbatas pada dua tes isolasi di atas. Tes fixture mempertahankan penambahan Phase 1H untuk FX, kalender nasional dan endpoint developer. Source guard Free Tier, ledger kuota, audit, persistence, canary dan fixture tetap utuh.

## File yang berubah pada Phase 1I

- Build dan CI: `backend/pom.xml`, `frontend/angular.json`, `frontend/package.json`, `frontend/package-lock.json`, `frontend/tsconfig.phivealpha.json`, `.github/workflows/validate.yml`.
- Aplikasi dan tes: `frontend/src/phivealpha/index.html`, `frontend/test-phivealpha.mjs`, `backend/src/test/java/com/phivealpha/IdxFoundationTest.java`, `backend/src/test/java/com/phivealpha/persistence/IdxPersistenceProfileTest.java`.
- Runbook: `AGENTS.md`, ketiga `README.md` di root, backend dan frontend, `docs/api/README.md`, `docs/architecture/README.md`, `docs/development/phivealpha-phase-1c.md`.
- Riwayat dan arsip: `docs/development/current-state.md`, `docs/development/session-log.md`, `docs/development/phivealpha-phase-1i.md`, `docs/development/production-release.md`, `docs/setup/ubuntu-server-mvp.md`, `infrastructure/README.md`.

## Risiko migrasi dan rollback

- Build default menggantikan profil Maven `-Pphivealpha`. JAR dan kelas tes kini berada di `backend/target/phivealpha/`, dan hasil Angular di `frontend/dist/phivealpha/browser`. Distribusikan JAR dan hasil browser spesifik dari build bersih. Output crypto lama yang terabaikan Git mungkin masih ada di worktree lama.
- Perintah historis canary 1D.2 dan 1H menggunakan path sebelum pemisahan. Kelas canary tetap tersedia di `backend/target/phivealpha/test-classes` setelah test compile. Entitlement akun tetap unverified dan verifikasi live memerlukan otorisasi tersendiri.
- Legacy bergantung pada skema, kredensial dan format artefak sendiri. Untuk rollback sumber, gunakan checkout terpisah di `3e96542` untuk build gabungan, atau tag recovery `coinmind-pre-phivealpha-20261118` (commit `10b7a67`) untuk baseline sebelum migrasi. Pertahankan worktree saat ini agar perubahan 1H tetap tersedia.
- Port HTTP dan PostgreSQL integration membutuhkan lingkungan dengan izin socket loopback. Format keluaran atau asumsi skrip release produksi perlu direview sebelum otomasi distribusi baru.

## Hasil pengujian

| Pemeriksaan lokal | Hasil |
| --- | --- |
| Baseline gabungan sebelum perubahan | `mvn -o clean verify` dengan Mockito Java agent. 288 tes ditemukan, 287 lulus, 1 skip socket. Unit frontend legacy lulus (10 status AI, 8 freshness, 41 berita, 41 tutorial, serta regresi lifecycle request). |
| Build dan suite Maven PhiveAlpha | `CI=true mvn -o -B -f backend/pom.xml clean verify` dan `mvn -o -B -f backend/pom.xml test` di luar mode CI lulus. Masing-masing 78 tes, 77 lulus dan 1 skip connector loopback Phase 1D.2. Endpoint fixture diuji dengan WebTestClient tanpa socket. |
| Executable JAR | Manifest dan metadata PhiveAlpha. Semua 158 kelas aplikasi dari paket IDX. Konfigurasi dan SQL IDX tersedia. Kelas, konfigurasi, SQL dan library khusus legacy yang diaudit absen. JAR berhasil memulai konteks fixture dari direktori kosong tanpa environment aplikasi dan tanpa server HTTP. |
| Frontend PhiveAlpha | Lima unit test API client dan build Angular produksi lulus dengan Node 22.23.2. Build juga lulus dari direktori sementara yang hanya memuat sumber IDX. Instalasi bersih offline di sandbox perlu `--ignore-scripts` setelah postinstall esbuild biasa mendapat EPERM. Artefak diperiksa untuk merek dan aset PWA legacy. |
| HTTP/browser loopback dan PostgreSQL R2DBC | Terhalang izin socket sandbox. Runner PostgreSQL berhenti sebelum membuat cluster sementara. |
| Integritas worktree | `git diff --check` lulus. Checksum awal Phase 1H dan pemeriksaan diff menjaga sumber runtime dan konfigurasi legacy. |

Phase 1I berjalan tanpa menyentuh service `coinmind-backend`, Nginx production, `/opt/coinmind`, `/var/www/coinmind` atau database production. Tidak ada deployment, request API.co.id/Gemini live, canary live, order, commit atau push.

Log Maven lokal tersedia di `/tmp/phivealpha-1i-baseline.log`, `/tmp/phivealpha-1i-verify-final.log` dan `/tmp/phivealpha-1i-test-local.log`.
