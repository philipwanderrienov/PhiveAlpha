# PhiveAlpha Phase 1L — Stock Research Dashboard & Frontend Integration

Baseline lokal `main` pada `c614812`. Dashboard tersedia dari navigasi **Stock Screener** pada `/stock-screener`. Rute dan kontrak Weekly Top 5 serta Stock Analysis tetap tersedia.

## Perilaku dashboard

- `IdxApi.stockScreener()` membaca `GET /api/idx/v1/stock-screener` melalui proxy dan client Angular yang sudah dipakai. Tipe TypeScript mengikuti `StockScreenerService.ScreeningSnapshot`, termasuk provenance nullable, hasil likuiditas, penilaian faktor, dan keputusan per simbol.
- Shortlist menampilkan maksimal lima baris sesuai urutan dan ranking yang diberikan backend. Buka kandidat dengan klik, tap, Enter, atau Spasi untuk melihat likuiditas, momentum, berita, bobot dan kontribusi faktor, serta sumber data. Bagian keputusan menampilkan penolakan likuiditas, masalah momentum, dan alasan penilaian saat tersedia.
- `FIXTURE` menunjukkan data sintetis. `DEVELOPMENT_PERSISTENCE` menunjukkan riwayat fixture dari penyimpanan development. Kualitas tampil terpisah dari mode. Timestamp snapshot dan provenance ditampilkan dalam UTC. Kualitas menggambarkan data pada cutoff yang dievaluasi backend.
- Skor riset muncul pada snapshot `COMPLETE` dengan tujuan `RESEARCH_ONLY`, penilaian `SCOREABLE`, dan kualitas faktor yang memadai. `INCOMPLETE`, `STALE`, `UNVERIFIED`, dan `UNAVAILABLE` masing-masing memiliki pesan. Dashboard menjelaskan shortlist kosong, timestamp atau detail yang belum tersedia, serta kegagalan request dengan tombol coba lagi.
- Layout satu kolom di layar kecil beralih ke dua kolom mulai 52 rem. Disclosure memakai elemen native dengan indikator fokus keyboard.

## Validasi pada host development

Gunakan Java 25 dan Node 22.23.2 atau versi yang didukung Angular 22. Jalankan dari root repository.

```bash
CI=true mvn -o -B -f backend/pom.xml clean verify
(cd frontend && npm test && npm run build)
git diff --check
```

Jalankan kedua proses ini di terminal terpisah dengan profil fixture default.

```bash
java -jar backend/target/phivealpha/phivealpha-backend.jar
```

```bash
cd frontend
npm start
```

1. Buka `http://127.0.0.1:4282/stock-screener`. Bandingkan ranking dengan respons `curl -fsS http://127.0.0.1:8182/api/idx/v1/stock-screener`. Fixture default berisi lima kandidat.
2. Periksa label `FIXTURE`, kualitas snapshot, data per, waktu pembuatan, cutoff, serta sumber universe dan kalender. Buka setiap kandidat dan keputusan yang belum lolos untuk memeriksa faktor, sumber harga, dan kode alasan.
3. Uji viewport sekitar 360 px dan 1280 px. Pastikan navigasi dan baris provenance tetap terbaca tanpa scroll horizontal. Gunakan Tab dan Enter atau Spasi untuk membuka kandidat. Verifikasi tautan saham dari Weekly Top 5 masih membuka Stock Analysis.
4. Blokir hanya request screener melalui DevTools dan buka kembali rutenya dari aplikasi. Pastikan pesan gagal muncul. Lepas blokir lalu klik **Coba lagi**.
5. Untuk menguji persistence secara terisolasi, jalankan `scripts/validation/phivealpha-persistence-it.sh --stock-screener -o` sesuai handoff Phase 1K. Runner membuat PostgreSQL sementara.

## Hasil lokal dan batas validasi

- `npm test` lulus dengan 13 tes. Cakupan meliputi GET, ranking dan detail, provenance dan rejection, kualitas dan skor tersembunyi, request gagal dan retry, serta navigasi ke Weekly Top 5 dan Stock Analysis.
- `npm run build` lulus untuk Angular PhiveAlpha. JSON fixture aktual dari WebFlux in-process juga berhasil dirender oleh komponen Angular dengan locale `id-ID`.
- Maven offline lulus dengan 94 tes lulus, satu skip connector loopback, dan nol gagal. Kontrak Java tetap sama.
- Sandbox menolak socket loopback dan startup Chromium. Pemeriksaan layout browser serta integrasi PostgreSQL melalui loopback tersisa untuk host development.
- Perubahan terbatas pada frontend dan dokumentasi. API provider live, order, automation, deployment, service production, database production, dan arsip crypto berada di luar eksekusi fase ini.
