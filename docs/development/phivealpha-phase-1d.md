# PhiveAlpha Phase 1D

Adapter `ApiCoIdHttpProvider` memakai WebClient dan Reactor untuk mengambil tujuh dataset IDX. Bean tersedia hanya dengan profil `phivealpha-api-co-id`. Runtime default memakai fixture Phase 1A tanpa kredensial. Profil HTTP tidak memulai fetch atau scheduler saat startup dan menolak startup pada CI. Service baru tidak membuka koneksi database, menyimpan payload ke disk, atau memanggil service persistence Phase 1C.

Method dataset bertipe `Mono<Snapshot<T>>` merupakan batas untuk caller internal. Rute Phase 1A memiliki label fixture dan kontrak harga referensi mingguan. Dalam profil HTTP rute ini fail closed dengan HTTP 503. Ranking fixture tetap tersedia di mode demo. Profil HTTP belum menerbitkan BUY atau SETUP READY. Data yang lolos parser tetap `UNVERIFIED` sampai kontrak, hak penggunaan, cakupan, adjustment, dan freshness diverifikasi lewat canary.

## Mapping endpoint

| Dataset / method | GET pada `/idx/v2` | Normalisasi dan batas |
| --- | --- | --- |
| Master / `instrumentMaster` | `/instruments?index=IHSG&limit=…` | Kode, nama, sektor, papan, tanggal pencatatan bila tersedia. Batas yang dikirim berasal dari jumlah simbol referensi lokal. Set akhir wajib sama persis dengan referensi berversi. Tanpa referensi, operasi berhenti sebelum HTTP. |
| Quote / `marketQuote` | `/chart/quote/{symbol}` | Last dan OHLC dalam rupiah, volume integral, turnover opsional, serta tanggal sesi. Snapshot parsial atau OHLC inkonsisten ditolak. Basis `UNKNOWN`. `prev_close` tidak dipakai untuk return mingguan. |
| OHLCV / `dailyOhlcv` | `/chart/daily/{symbol}?timeframe=1d&from=…&to=…&order=asc` | Rentang sesi terkonfirmasi, nilai valid, coverage lengkap dan bar telah melewati penutupan sesi pada `as_of`. Basis `ADJUSTED` mengikuti dokumentasi endpoint untuk split/bonus. Penyesuaian dividen, revisi, dan versi adjustment masih perlu diperiksa. |
| Berita / `marketNews`, `symbolNews` | `/news` atau `/news/{symbol}` dengan `category=news` | ID, judul, kode emiten yang dinormalisasi dan dideduplikasi, sumber dan URL opsional. Timestamp publikasi memerlukan offset eksplisit dan tidak boleh sesudah observasi lokal. Observasi fetch disimpan dalam snapshot. Provenance first-seen historis dan kapan berita layak dipakai untuk scoring memerlukan validasi terpisah. |
| Corporate actions / `corporateActions` | `/corporate-actions/{symbol}` | Tipe, tanggal pengumuman/ex/record/pay, nominal, rasio lama/baru. Cakupan mengikuti potongan endpoint terdokumentasi dan tidak dipakai sebagai riwayat lengkap. |
| Foreign flow / `foreignFlow` | `/foreign-flow/{symbol}?from=…&to=…&order=asc` | Nilai beli, jual, dan net rupiah. Net wajib sama dengan beli dikurangi jual, serta tanggal wajib berada di rentang sesi terkonfirmasi dan telah tutup pada `as_of`. |
| Broker flow / `brokerFlow` | `/broker-flow/{symbol}?at=…&investor_type=all` | Tanggal wrapper harus sama dengan sesi yang diminta. Bucket waktu divalidasi terhadap jam sesi. Net rupiah dan net lot mempertahankan tanda. Kode per bucket wajib ada di daftar broker wrapper. |

Rute, header, envelope, cursor, dan adjustment yang disebut di atas merujuk pada dokumentasi publik. Fixture JSON sintetis memvalidasi parser offline. Entitlement akun, batas efektif, field nullable dalam produksi, format broker time dan satuan volume quote, timezone metadata, semantik lag, serta kesesuaian tanggal corporate action masih perlu dicocokkan dengan respons aktual. [Referensi IDX API.co.id](https://docs.api.co.id/products/idx-market-data/) · [Autentikasi API.co.id](https://docs.api.co.id/authentication/)

Simbol berupa alfanumerik ASCII yang ditrim dan diubah ke huruf besar. Nilai rupiah diproses sebagai `BigDecimal` tanpa konversi lewat `double`. Volume numerik memakai presisi integral 64 bit. Broker lot tetap dalam lot. Timestamp absolut menjadi `Instant` dan tanggal serta jam lokal memakai `Asia/Jakarta`. Provenance snapshot membawa dataset, `as_of`, waktu fetch, sumber/versi kalender dan versi referensi universe jika relevan.

## Kuota, cache dan audit

- Setiap percobaan yang lolos admission mengurangi satu unit budget lokal. Halaman lanjutan, retry, kegagalan, timeout dan pembatalan terhitung. Cache hit dan panggilan yang diblokir sebelum request tidak mengurangi budget. Budget berlaku sepanjang umur satu proses, mencakup semua dataset dan tidak memiliki reset berkala. Gunakan satu proses canary dan cocokkan hit aktual dengan dashboard provider. Pemakaian akun bersama, restart, biaya per endpoint, dan kuota paket tidak disimpulkan dari budget lokal.
- Minimum interval antar-request berlaku global. Pagination menunggu interval sebelum meminta halaman selanjutnya. Cursor diperlakukan sebagai token opaque dan hanya dipakai sebagai parameter query yang di-encode. Cursor berulang, identitas baris duplikat, cursor kosong, count mismatch, terminasi yang tidak eksplisit, dan batas halaman/baris menggagalkan batch tanpa mengembalikan baris parsial. Corporate actions dan quote dibatasi satu respons.
- Deadline per request dan operasi membatasi I/O. Admission juga membatasi concurrency. Hanya timeout, kegagalan transport, dan HTTP 500/502/503/504 dapat diulang dengan retry eksplisit maksimal dua kali. Default retry nol. 401/402/403 mengunci transport sampai proses diganti. 429 menutup akses lintas dataset sesuai `Retry-After` atau cooldown lokal jika header tidak dapat digunakan. Circuit breaker membuka setelah ambang kegagalan transient dan hanya mengizinkan satu probe setelah cooldown.
- Cache in-memory terbatas per dataset serta parameter. Request identik yang sedang berjalan berbagi satu hasil. Hanya batch yang lengkap dan lolos parser yang masuk cache. TTL mengatur berapa lama respons dapat dipakai ulang. Freshness dinilai ulang tiap read, termasuk cache hit, dari usia konservatif yang diperoleh dari `as_of` serta `stale_seconds` bila tersedia. TTL dan freshness adalah pengaturan terpisah.
- Audit INFO mencatat dataset, nomor admission, status HTTP, kategori hasil, sisa budget, jumlah baris dan timestamp freshness. Audit tidak memuat API key, URL request, cursor, atau body. Redirect dan retry tambahan Reactor Netty dinonaktifkan. Buffer HTTP dibatasi 1 MiB.
- `UNAVAILABLE` meliputi gangguan transport, auth, quota dan circuit. `INCOMPLETE` menandai schema, pagination, baris, atau cakupan yang gagal validasi. `STALE` menandai ambang usia atau sesi quote yang tertinggal. `UNVERIFIED` menandai provenance, kebijakan, cakupan kalender, atau hasil canary yang belum dapat dibuktikan. Snapshot stale atau unverified dapat memuat baris untuk diagnosis internal.

## Profil opt-in dan kalender BEI

Gunakan credential privat `API_CO_ID_KEY` hanya di backend. `API_CO_ID_BASE_URL` mengizinkan origin HTTPS resmi `https://use.api.co.id` dengan port default atau 443. Pengaturan wajib lain meliputi:

| Environment variable | Fungsi |
| --- | --- |
| `API_CO_ID_MIN_REQUEST_INTERVAL` | Jarak minimum antar-start yang dipilih setelah entitlement dan rate limit akun diperiksa. |
| `API_CO_ID_REQUEST_BUDGET` | Batas percobaan selama satu proses. |
| `API_CO_ID_CACHE_TTL` | Masa cache lokal yang disetujui. |
| `API_CO_ID_MAX_PAGES`, `API_CO_ID_MAX_ROWS` | Batas kerja lokal per dataset. |
| `API_CO_ID_CALENDAR_PATH` | Path absolut manifest kalender lokal yang sudah ditinjau. |

Timeout, timeout operasi, concurrency, retry, delay, breaker, dan kapasitas cache dapat diatur melalui `API_CO_ID_TIMEOUT`, `API_CO_ID_OPERATION_TIMEOUT`, `API_CO_ID_CONCURRENCY`, `API_CO_ID_MAX_RETRIES`, `API_CO_ID_RETRY_DELAY`, `API_CO_ID_BREAKER_THRESHOLD`, `API_CO_ID_BREAKER_COOLDOWN`, dan `API_CO_ID_CACHE_ENTRIES`. Untuk master, set `API_CO_ID_EXPECTED_UNIVERSE` ke daftar kode terverifikasi dipisahkan koma, dan `API_CO_ID_UNIVERSE_VERSION` ke versi referensi. Atur freshness per dataset melalui properti Spring `phivealpha.api-co-id.max-age.<dataset>` hanya setelah kriteria disepakati. Tanpa properti itu status tetap unverified kecuali ada alasan kualitas lain, seperti quote dari sesi yang tertinggal.

Manifest JSON memuat `source`, `version`, dan `days`. Setiap hari memiliki `date`, `kind` (`REGULAR`, `SPECIAL`, atau `HOLIDAY`), dan array `sessions` berisi `open`/`close` lokal. Hari libur memiliki array kosong. Hari aktif dapat membawa beberapa interval untuk jeda. Jenis `SPECIAL` memakai interval khusus yang ditetapkan per tanggal. Seluruh hari dalam rentang harus tercantum, termasuk akhir pekan dan libur. Tanggal di luar cakupan gagal tertutup. Jadwal review harus mencocokkan pasar/papan yang digunakan dan mencatat revisi pengumuman resmi. Fixture kalender berada di test resources dan tanggalnya sengaja sintetis. [Jadwal Libur Bursa BEI](https://www.idx.co.id/id/tentang-bei/jadwal-libur-bursa) · [Jam Perdagangan BEI](https://www.idx.co.id/id/produk/mekanisme-dan-jam-perdagangan)

## Pengujian dan inventaris

- Produksi berubah di `backend/src/main/java/com/phivealpha/marketdata/apicoid/`, kontrak provider, provider fixture, `screening/WeeklyScreening.java`, dan konfigurasi `backend/src/main/resources/phivealpha.yml`.
- Pengujian offline ditambahkan di `backend/src/test/java/com/phivealpha/marketdata/apicoid/` dan `backend/src/test/resources/phivealpha/api-co-id/`. Tes konteks fixture memeriksa bahwa live provider tidak aktif.
- Pengujian HTTP memakai `ExchangeFunction` dalam proses. Tidak ada test yang memanggil gateway API.co.id. Pengujian konteks profil memverifikasi wiring tanpa memanggil method fetch.
- Suite Maven offline **PASS**, 238 tes dengan 0 gagal/error/skip. Termasuk 17 kasus adapter HTTP dan 1 tes profil baru. Sandbox memerlukan Mockito Java agent untuk suite lama.
- Tes profil dengan `CI=true` **PASS** dan mengonfirmasi startup profil network diblokir sebelum connector dibuat.
- Paket backend PhiveAlpha dan CoinMind **PASS**. Kedua build produksi Angular **PASS** dengan Node 22. Build CoinMind masih mengeluarkan peringatan template yang sudah ada.
- Live canary **NOT RUN**, nol request ke gateway dan nol konsumsi kuota. Integration PostgreSQL Phase 1C tetap opt-in dan di luar suite unit.

```bash
# Dari root repo pada sandbox yang memblokir self-attach
JDK_JAVA_OPTIONS="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar" \
  mvn -o -f backend/pom.xml test
mvn -o -f backend/pom.xml -Pphivealpha -DskipTests package
mvn -o -f backend/pom.xml -DskipTests package
(cd frontend && npm run build:phivealpha && npm run build)
```

## Lisensi dan live canary terkontrol

BEI membedakan hak penggunaan display, non-display, dan redistribusi. Sebelum canary, konfirmasi tertulis dari vendor mengenai entitlement dan biaya per rute, penggunaan internal, retensi cache dan histori, data turunan, redistribusi UI, serta hak atas judul/tautan berita dan data broker. Nilai badge publik dan contoh respons dokumentasi tidak membuktikan hak untuk akun PhiveAlpha. [Layanan Data BEI](https://www.idx.co.id/id/produk/layanan-data-bei/) · [Ringkasan produk API.co.id](https://api.co.id/api-saham-indonesia/)

1. Setelah persetujuan terpisah, jalankan harness satu kali pada host development terisolasi, di luar CI, tanpa profil persistence. Gunakan key khusus scope IDX. Verifikasi dulu apakah key sandbox mendukung IDX. Muat kalender BEI dengan versi resmi dan batas akun yang sudah dikonfirmasi.
2. Mulai dari satu pemanggilan internal `marketQuote` untuk satu simbol yang disetujui, dengan `request-budget=1`, `max-pages=1`, concurrency satu dan retry nol. Jangan menambah rute publik atau job polling untuk canary. Simpan hanya metadata aman yang diizinkan dan cocokkan satu admission dengan dashboard kuota.
3. Cocokkan simbol, unit, field kosong, timezone, usia data, waktu sesi, dan volume dengan referensi yang berizin. Hentikan sesi pada 401/402/403/429, masalah kuota, status tidak terduga, atau kontrak yang menyimpang. Putuskan egress dan rotasi key canary setelah selesai.
4. Tambahkan satu kasus yang disetujui per dataset dengan anggaran terpisah. Untuk pagination, cek cursor, terminasi dan setiap baris terhadap snapshot universe yang berversi. Untuk OHLCV dan berita, buktikan basis adjustment dan waktu ketersediaan historis sebelum memasukkannya ke screening. Siapkan persetujuan terpisah untuk perubahan status, penerbitan sinyal, persistensi atau tampilan.
