# PhiveAlpha Phase 1A — IDX Application Foundation

PhiveAlpha memakai Angular 22, Java 25, Spring Boot 4.1.1 dan WebFlux. Dependensi Spring Data R2DBC dan driver PostgreSQL tersedia untuk fase persistensi. Aplikasi fixture mengecualikan autokonfigurasi koneksi R2DBC dan mematikan inisialisasi SQL. Paket `com.phivealpha` memiliki entry point dan konfigurasi sendiri sehingga pemindaian komponen tidak mencakup CoinMind.

## Menjalankan demo lokal

Dari root repo, gunakan Java 25, Maven, Node yang didukung Angular 22 (build ini memakai 22.23.2) dan dependensi frontend yang sudah terpasang.

```bash
mvn -o -f backend/pom.xml -Pphivealpha -DskipTests package
java -jar backend/target/phivealpha-backend.jar
```

Di terminal kedua:

```bash
cd frontend
npm run start:phivealpha
```

Buka `http://127.0.0.1:4282`. Dev server mem-proxy API IDX ke `127.0.0.1:8182`. `npm run build:phivealpha` menghasilkan `frontend/dist/phivealpha/browser`. Build default Maven dan `npm run build` tetap memakai entry point dan output CoinMind.

## Kontrak fixture

`ApiCoIdProvider` adalah kontrak internal reaktif untuk daftar snapshot saham, lookup kode dan headline. Implementasi fixture menyimpan enam snapshot sintetis dan dua headline bertanda waktu di memori. Kontrak wire, autentikasi, kuota dan cakupan data API.co.id masih perlu diverifikasi pada fase integrasi.

| GET di bawah `/api/idx/v1` | Halaman | Hasil |
| --- | --- | --- |
| `/stocks` | Overview | Snapshot penutupan dalam rupiah per saham |
| `/weekly-top-five` | Weekly Top 5 | Maksimal lima saham menurut perubahan persentase atas penutupan pekan lalu, dibulatkan dua desimal, dengan kode sebagai pemecah seri |
| `/stocks/{symbol}` | Stock Analysis | Detail snapshot. Pencarian kode tidak peka huruf besar/kecil. Kode di luar fixture menghasilkan 404 |
| `/headlines` | News Intelligence | Judul sintetis beserta sumber, kode dan waktu publikasi |
| `/paper-trade/preview?symbol=BBCA&lots=2` | Paper Trading | Estimasi bruto dari 1–10.000 lot utuh, 100 saham per lot, sebelum biaya. Masukan lot tidak valid menghasilkan 400 |

Paper Trading menghitung estimasi tanpa menulis transaksi atau posisi. Weekly Top 5 mencakup universe fixture. Stock Analysis dan News Intelligence menampilkan snapshot dan headline contoh.

## Dependensi yang harus diputus sebelum penghapusan crypto

| Area | Rujukan di repo dan pekerjaan pengganti |
| --- | --- |
| Pasar dan indikator | `com.coinmind.config`, `market` dan `indicator` mengikat konfigurasi Binance Spot, jenis simbol/timeframe, feed/cache dan indikator. `trade` dan frontend chart mengonsumsi hasilnya. Pindahkan konsumen ke model sekuritas, kalender dan sesi IDX sebelum melepas feed crypto. |
| Advisor, Futures dan paper replay | `com.coinmind.trade` memakai harga/indikator dan konteks berita, AI serta Manifold. Futures replay memakai repository dan SQL arsip `backend/src/main/resources/db/futures_paper_replay_*.sql`. Pertahankan aturan retensi/recovery dan pisahkan simulasi saham serta alur replay crypto sebelum pensiun. |
| Berita, AI, prediction market, notifikasi | `com.coinmind.news`, `ai`, `manifold`, `polymarket` dan `notification` memiliki konfigurasi/scheduler, endpoint, scoring, ledger Gemini dan notifikasi/billing untuk alur lama. Inventarisasi konsumen API serta kebijakan arsip/retensi dan putuskan subscription lama sebelum menghapus paket atau konfigurasi. |
| Frontend lama | `frontend/src/app`, `market-api.service.ts`, model pasar, chart, tutorial, push, dan service worker/manifest terikat pada rute serta aset CoinMind. Migrasikan pemanggil, deep link dan cache PWA sebelum menghentikan UI. |
| Persistence dan operasi | `application.yml`, `.env.example`, Docker Compose, `deploy.sh`, `infrastructure/`, `scripts/release/` dan `scripts/validation/` memakai konfigurasi, artefak, rute health, skema atau runbook CoinMind. Pisahkan jalur build/runtime dan tinjau migrasi data serta rollback sebelum melepas kontrak tersebut. Pertahankan tag `coinmind-pre-phivealpha-20261118`. |

## Pekerjaan berikutnya

1. Verifikasi spesifikasi dan lisensi API.co.id, kalender bursa, cakupan emiten, penyesuaian aksi korporasi dan definisi freshness sebelum membangun adapter.
2. Rancang skema IDX serta uji repositori R2DBC dengan PostgreSQL terisolasi. Tinjau migrasi sebagai langkah eksplisit terpisah.
3. Tambahkan data historis, aturan screening dan provenance berita, lalu tambahkan biaya serta penyimpanan posisi untuk simulasi. Tentukan kelanjutan setiap konsumen crypto sebelum penghapusan.

## Daftar file berubah

### Backend

- `backend/pom.xml`
- `backend/src/main/java/com/phivealpha/IdxController.java`
- `backend/src/main/java/com/phivealpha/PhiveAlphaApplication.java`
- `backend/src/main/java/com/phivealpha/marketdata/ApiCoIdProvider.java`
- `backend/src/main/java/com/phivealpha/marketdata/FixtureApiCoIdProvider.java`
- `backend/src/main/java/com/phivealpha/marketdata/IdxQuote.java`
- `backend/src/main/java/com/phivealpha/newsintelligence/IdxHeadline.java`
- `backend/src/main/java/com/phivealpha/risk/PaperTradePreview.java`
- `backend/src/main/java/com/phivealpha/screening/WeeklyScreening.java`
- `backend/src/main/java/com/phivealpha/stock/IdxStock.java`
- `backend/src/main/resources/phivealpha.yml`
- `backend/src/test/java/com/phivealpha/IdxFoundationTest.java`

### Frontend

- `frontend/angular.json`
- `frontend/package.json`
- `frontend/proxy.phivealpha.json`
- `frontend/src/phivealpha/app.component.ts`
- `frontend/src/phivealpha/app.routes.ts`
- `frontend/src/phivealpha/core/idx-api.ts`
- `frontend/src/phivealpha/index.html`
- `frontend/src/phivealpha/main.ts`
- `frontend/src/phivealpha/news-intelligence/news-intelligence.page.ts`
- `frontend/src/phivealpha/overview/overview.page.ts`
- `frontend/src/phivealpha/paper-trading/paper-trading.page.ts`
- `frontend/src/phivealpha/stock-analysis/stock-analysis.page.ts`
- `frontend/src/phivealpha/styles.css`
- `frontend/src/phivealpha/weekly-top-five/weekly-top-five.page.ts`
- `frontend/tsconfig.phivealpha.json`

### Dokumentasi

- `docs/development/current-state.md`
- `docs/development/phivealpha-phase-1a.md`
- `docs/development/session-log.md`

## Verifikasi lokal

- Maven offline pada Java 25.0.4.1 menjalankan 215 tes dengan nol gagal, error, atau dilewati. Lima tes baru memulai konteks Spring melalui entry point PhiveAlpha, menguji rute WebFlux tanpa membuka port, dan memeriksa isolasi dari scheduler CoinMind serta koneksi R2DBC.
- `npm run build:phivealpha` dan `npm run build` lulus dengan Angular 22.1.7 dan Node 22.23.2. Build CoinMind masih memiliki peringatan template lama.
- Uji browser dengan backend dan proxy lokal terhalang izin socket sandbox. Alur navigasi browser, rendering responsif, dan penyambungan proxy masih perlu smoke test.
