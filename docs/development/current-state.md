## 2026-11-22 — PhiveAlpha Phase 1O-A (lokal)

Preflight histori IDX mengaudit satu cutoff dengan target default 250 sesi tertutup dari kalender berversi. Hasil memisahkan kelengkapan candle, ketersediaan temporal, provenance, status instrumen dan corporate action, serta IHSG. Workflow riset memanggil preflight secara eksplisit. Replay Phase 1G mempertahankan kontrak yang sama. Runner PostgreSQL development tertahan probe loopback. Bukti fase ini menggunakan fixture sintetis. Maven offline 112 lulus, termasuk 18 tes audit baru, dan satu skip loopback. [Handoff Phase 1O-A](phivealpha-phase-1o-a.md)

## 2026-11-22 — PhiveAlpha Phase 1N.1 (lokal)

Kartu Screener dipadatkan dengan target sentuh 44 px. Skor tampil dengan maksimal dua desimal Indonesia, sementara nilai API tetap tersedia pada rincian dan urutan backend tetap dipakai saat skor sama. Navigasi bawah memakai ikon garis seragam. Unit frontend 16 lulus, build Angular lulus, dan git diff --check lulus. Review Chromium dengan fixture lokal mencakup 360, 768, dan 1280 px. Pemeriksaan meliputi ukuran target, overflow, urutan kandidat, serta Enter dan Spasi untuk membuka riset dan rincian, pengembalian fokus, dan navigasi keyboard. [Handoff Phase 1N](phivealpha-phase-1n.md#phase-1n1--mobile-usability-polish)

## 2026-11-22 — PhiveAlpha Phase 1N (lokal)

Redesain Angular light-only dengan navigasi bawah mobile dan sidebar desktop. Screener menampilkan maksimal lima kandidat dan membuka satu tampilan riset dengan bukti ringkas, rincian teknis tertutup, dan alasan saham belum lolos. API fixture dan rute lama tetap berlaku. Regresi Maven offline mencatat 94 lulus dan satu skip loopback. Panduan pemeriksaan browser pada host tersedia di [handoff Phase 1N](phivealpha-phase-1n.md).

## 2026-11-21 — PhiveAlpha Phase 1M (lokal)

Modernisasi light-only menambahkan sidebar desktop, navigasi compact, token visual bersama, dan pembaruan keenam halaman. Shortlist Screener memilih satu detail yang dapat ditutup, dengan kualitas, provenance dan guard skor Phase 1L tetap berlaku. Unit frontend 13 lulus, build Angular lulus, dan Maven offline 94 lulus dengan satu skip loopback. Aturan CSS diperiksa pada 360, 768, dan 1280 px. Pemeriksaan visual browser tersisa untuk host development. [Handoff Phase 1M](phivealpha-phase-1m.md)

## 2026-11-21 — PhiveAlpha Phase 1L (lokal)

Dashboard Stock Screener mengonsumsi endpoint fixture Phase 1K melalui client Angular yang sama. Shortlist maksimal lima membuka detail likuiditas, faktor momentum dan berita, provenance, serta keputusan yang belum lolos. Skor riset mengikuti kualitas snapshot dan mode sumber ditampilkan. Weekly Top 5 dan Stock Analysis tetap terhubung. Unit frontend 13 lulus, build Angular lulus, dan Maven offline 94 lulus dengan satu skip loopback. Pemeriksaan browser dan PostgreSQL loopback tersisa untuk host development. [Handoff Phase 1L](phivealpha-phase-1l.md)

## 2026-11-21 — PhiveAlpha Phase 1K (lokal)

Stock Screener menghubungkan history Phase 1J, Liquid Universe, dan News-Driven Scoring pada cutoff yang sama. Endpoint GET `/api/idx/v1/stock-screener` menyajikan fixture secara default dan menyediakan opt-in pembacaan fixture historis di PostgreSQL development. Snapshot mencatat eligibility, alasan, faktor, dan provenance dengan ranking riset maksimum lima. Maven offline 94 lulus dan satu skip loopback. Runner PostgreSQL sementara tersedia, tetapi sandbox menolak socket. [Handoff Phase 1K](phivealpha-phase-1k.md)

## 2026-11-21 — PhiveAlpha Phase 1J (lokal)

Pipeline internal OHLCV harian memvalidasi fixture terhadap kalender BEI berversi, menyimpan candle melalui profil persistence development, dan memasok harga unadjusted ke screening berdasarkan cutoff dan lookback sesi. Adapter Daily HTTP tetap unverified untuk write. Maven offline CI mencatat 86 tes lulus dan satu skip. SQL PostgreSQL 17 single-user lulus untuk turnover opsional, idempotensi dan cutoff. Runner R2DBC tertahan izin socket. [Handoff Phase 1J](phivealpha-phase-1j.md)

## 2026-11-21 — PhiveAlpha Phase 1H SIT-02B (lokal)

Runner PostgreSQL 17 mendukung mode audit dengan database dan role `phivealpha_dev_audit_it`. Tes end-to-end memeriksa metadata transport, ledger setelah restart komponen, dan kegagalan tulis. Warning audit kini memuat UUID reservasi. Maven 78 lulus dan 1 skip connector loopback. Regresi kegagalan storage in-process dan uji SQL PostgreSQL single-user lulus. SIT PostgreSQL R2DBC tertahan izin socket sandbox. Phase 1I sebelumnya lulus di host berdasarkan hasil yang dilaporkan pengguna. [Handoff SIT-02B](phivealpha-sit-02b.md)

## 2026-11-21 — PhiveAlpha Phase 1I (lokal)

PhiveAlpha menjadi build default Maven dan Angular. Artefak backend `backend/target/phivealpha/phivealpha-backend.jar` memuat entry point, kelas dan konfigurasi IDX. Frontend di `frontend/dist/phivealpha/browser` menampilkan lima halaman riset berbasis fixture. CI menjalankan Maven verify, unit API client frontend dan build Angular. Maven lokal 77 lulus dan satu skip connector loopback. Lima unit frontend dan build Angular lulus. HTTP/browser loopback serta PostgreSQL integration masih terhalang sandbox. Runbook aktif ada di [README](../../README.md), dengan audit isolasi dan hasil pengujian di [handoff Phase 1I](phivealpha-phase-1i.md).

## Riwayat Phase 1A–1H

Perintah build dan hasil verifikasi di entri berikut mencatat konfigurasi tiap fase. Gunakan runbook Phase 1I untuk build saat ini.

### 2026-11-20 — PhiveAlpha Phase 1H (lokal)

Transport IDX berbagi ledger per produk dengan adapter USD/IDR dan hari libur nasional. Live profiles memerlukan bukti entitlement Free dan window reset yang masih berlaku. Developer endpoint memisahkan estimasi budget internal dan observasi kuota vendor. Default tetap fixture. Canary Daily BBCA tersedia dengan guard satu GET dan belum dijalankan. Kalender vendor IDX tetap audit-only. Maven offline 287 lulus dengan satu skip loopback Phase 1D.2. Kedua paket backend, unit frontend dan build Angular lulus. [Handoff Phase 1H](phivealpha-phase-1h.md)

### 2026-11-20 — PhiveAlpha Phase 1G (lokal)

`HistoricalReplayService` mengevaluasi ranking riset pada cutoff point-in-time melalui universe dan scoring yang ada. Replay sintetis memakai entry open berikutnya, exit 2/5/10 sesi, biaya dan batas lot/likuiditas, baseline likuiditas dan IHSG sintetis, serta split kronologis dengan embargo. Posisi unresolved tetap tercatat. Maven offline 271 lulus, satu uji loopback Phase 1D.2 dilewati karena sandbox. Kedua paket backend, unit frontend, dan kedua build Angular lulus. [Handoff Phase 1G](phivealpha-phase-1g.md)

### 2026-11-19 — PhiveAlpha Phase 1F (lokal)

`NewsDrivenScoringService` memberi ranking riset maksimum lima kandidat dari universe eligible dengan data lengkap pada cutoff. Lima faktor berbobot configurable menyimpan breakdown dan provenance. Duplikasi, ketersediaan point-in-time, dan kualitas source diperiksa sebelum ranking. Bobot dan koefisien masih hipotesis fixture. Maven offline 264 lulus dan satu uji loopback Phase 1D.2 dilewati karena sandbox. Kedua paket backend, unit frontend, dan build Angular lulus. Endpoint fixture tetap, tanpa sinyal atau request live. [Handoff Phase 1F](phivealpha-phase-1f.md)

### 2026-11-19 — PhiveAlpha Phase 1E (lokal)

`LiquidUniverseService` menilai saham biasa IDX dari referensi dan kalender berversi serta candle EOD yang tersedia pada cutoff. Eligibility, kualitas data, alasan penolakan, dan ranking likuiditas deterministik tersedia untuk caller internal. Policy masih hipotesis, hasil tidak dibatasi lima, dan endpoint demo tetap memakai fixture. Maven offline: 257 lulus, satu uji loopback Phase 1D.2 dilewati karena sandbox. Kedua paket backend dan build Angular lulus. [Handoff Phase 1E](phivealpha-phase-1e.md)

### 2026-11-19 — PhiveAlpha Phase 1D.2 (lokal)

Canary developer standalone untuk quote BBCA menuntut `--confirm-live` dan persetujuan entitlement lokal yang berlaku. Budget/concurrency/pages masing-masing satu, retry nol, laporan metadata aman, dan hasil parser lolos tetap unverified atau stale. Maven offline mencatat 250 lulus dan satu uji connector loopback dilewati karena sandbox. Kedua paket backend dan build Angular lulus. Live canary menunggu uji connector pada host development dan persetujuan akun/vendor. [Handoff Phase 1D.2](phivealpha-phase-1d2.md)

### 2026-11-18 — PhiveAlpha Phase 1D (lokal)

Adapter API.co.id opt-in menyediakan tujuh dataset IDX untuk caller internal, dengan budget per proses, throttle, circuit breaker, cache dan audit freshness. Kalender BEI berversi dan referensi universe memagari cakupan. Fixture tetap default. Profil HTTP memblokir rute Phase 1A dan screening selama kontrak masih unverified, serta ditolak di CI. Maven offline 238 tes lulus dengan Mockito Java agent, kedua paket backend dan kedua build Angular lulus. Live canary belum dijalankan. [Handoff Phase 1D](phivealpha-phase-1d.md)

### 2026-11-18 — PhiveAlpha Phase 1C (lokal)

Persistence IDX tersedia melalui profil opt-in `phivealpha-persistence` dan migrasi SQL manual pada klaster development terisolasi. Service reaktif mencakup master instrumen, harga NUMERIC per basis, berita dengan deduplikasi dan cutoff artikel/emiten, serta audit ingestion. Mode default dan endpoint tetap memakai fixture. Maven 220 tes lulus dengan Mockito Java agent, paket backend dan build Angular kedua target lulus. DDL dan rollback lulus di PostgreSQL single-user. Integration R2DBC dan smoke browser berstatus NOT RUN karena pembatasan sandbox. [Handoff Phase 1C](phivealpha-phase-1c.md)

### 2026-11-18 — PhiveAlpha Phase 1A (lokal)

Fondasi aplikasi IDX tersedia pada `main` dengan entry point Spring Boot/WebFlux dan target Angular terpisah. API fixture menyediakan snapshot, Weekly Top 5, headline dan estimasi lot untuk kelima halaman. R2DBC/PostgreSQL tersedia sebagai dependensi. Mode fixture mematikan koneksi dan inisialisasi SQL. CoinMind tetap tersedia melalui build default. Kontrak, cara menjalankan demo, dan dependensi penghapusan crypto tercatat di [handoff Phase 1A](phivealpha-phase-1a.md).

Status proyek crypto sebelum PhiveAlpha disimpan di [arsip migrasi](coinmind-current-state-archive.md).
