# PhiveAlpha Phase 1H — Free API Integration & Quota Management

Baseline lokal `3e96542` pada `main`. Default tetap fixture tanpa kredensial, jaringan, atau koneksi database. Adapter IDX menggunakan `ApiCoIdHttpProvider` dan parser Phase 1D. Ketiga produk berbagi transport WebClient yang sudah ada. Masing-masing produk memiliki gate, budget bulanan internal, entitlement endpoint, dan batas cache sendiri.

## Kontrak dan entitlement

Status entitlement akun untuk semua endpoint di bawah ini **UNVERIFIED**. Label paket publik hanya menjadi dasar audit awal. Operator perlu mencocokkan kontrak, jenis key, hak penggunaan, tarif, periode reset, dan sisa kuota akun sebelum mengisi bukti lokal.

| Produk / endpoint | Tier publik dan perilaku |
| --- | --- |
| IDX `/idx/v2/instruments` | Publik Free. Filter `index=IHSG` dan hasil pagination wajib cocok dengan manifest universe lokal berversi. Cakupan seluruh efek, IPO/delisting dan ukuran halaman efektif perlu diverifikasi. |
| IDX `/idx/v2/chart/daily/{symbol}` | Publik Free. OHLCV EOD dan cursor divalidasi oleh adapter existing, termasuk kalender lokal, waktu penutupan, identitas dan kelengkapan sesi. Adjustment split/bonus mengikuti kontrak Phase 1D. Dividen, revisi, dan distribusi volume masih perlu audit. |
| IDX `/idx/v2/news` dan `/idx/v2/news/{symbol}` | Keduanya publik Free, grant terpisah. Validasi envelope, pagination dan timestamp offset eksplisit. Waktu observasi fetch tidak dianggap first-seen historis. |
| IDX `/idx/v2/market/calendar` dan `/idx/v2/market/holidays` | Publik Free. Yang pertama adalah kalender corporate action. Yang kedua daftar libur bursa. Keduanya belum terhubung ke runtime karena kontrak respons dan entitlement akun belum terbukti. Manifest resmi BEI yang ditinjau tetap otoritas sesi dan jam khusus. |
| IDX quote, corporate actions per simbol, foreign flow dan broker flow | Publik Starter/Trader. Dikeluarkan dari allowlist runtime Free. Metode adapter Phase 1D tetap tersedia untuk fixture dan harness yang telah disetujui. |
| FX `/currency/exchange-rate?pair=USDIDR` | Tier akun belum dibuktikan. `ExchangeRateProvider.usdIdr()` memvalidasi envelope, `USDIDR`, rate `BigDecimal` positif, dan `data.updated_at` sebagai Unix epoch milidetik. Offset teks, unit detik, dan waktu masa depan ditolak. `asOf`, `fetchedAt`, provider dan versi timestamp ada pada snapshot. TTL default 30 menit dan max age dua jam. |
| Nasional `/holidays/indonesia/?year=YYYY&page=1` | Tier akun belum dibuktikan. `IndonesiaHolidayCalendarProvider.holidays(year)` menerima 2000–2100, memvalidasi tanggal ISO, tahun, ID unik, flag nasional dan paging lengkap. Bila ada lebih dari satu halaman atau count ambigu, hasil `INCOMPLETE` tanpa halaman tambahan. Hanya flag nasional yang dinormalisasi. Cache per tahun menyimpan versi konten SHA-256, waktu observasi dan TTL 30 hari. `cachedVersion(year, version)` meminta versi cache tertentu tanpa request HTTP. Versi lama tidak diarsipkan. Tahun tanpa bukti coverage juga gagal tertutup. |

[Produk IDX dan tier publik](https://api.co.id/api-saham-indonesia/) · [Kontrak kurs](https://docs.api.co.id/products/exchange-rate/) · [Kontrak hari libur](https://docs.api.co.id/products/indonesian-holidays/)

Snapshot FX bersifat `RESEARCH_ONLY`, tanpa koneksi ke bobot `NewsDrivenScoringService`. `VERIFIED` memerlukan hasil valid, fresh, kontrak yang telah direview, dan entitlement Free yang belum kedaluwarsa. `UNVERIFIED` berlaku saat bukti kontrak/as-of belum memadai. `STALE` dievaluasi ulang setiap read, termasuk cache hit. `INCOMPLETE` menandai schema invalid, sedangkan `UNAVAILABLE` menandai transport atau quota yang menolak. Kalender nasional tidak memiliki timestamp versi dari vendor dalam kontrak ini, sehingga menyimpan waktu observasi lokal dan mempertahankan kualitas `UNVERIFIED`. Perbedaan dengan hari bursa tidak mengubah BEI.

## Opt-in dan konfigurasi

| Environment | Kegunaan |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Profil terpisah `phivealpha-api-co-id`, `phivealpha-api-co-id-fx`, `phivealpha-api-co-id-holiday` dan `phivealpha-developer`. |
| `API_CO_ID_KEY` | Key privat yang diinjeksikan ke proses. |
| `PHIVEALPHA_API_APPROVAL_PATH` | Path absolut manifest lokal bukti entitlement, jendela reset, dan izin metadata opsional. |
| `PHIVEALPHA_API_QUOTA_LEDGER_PATH` | Path absolut jurnal di storage development lokal persisten. |
| `PHIVEALPHA_DEVELOPER_TOKEN` | Token akses privat untuk endpoint status, 24–128 karakter alfanumerik, garis bawah, atau tanda hubung. |
| `API_CO_ID_CALENDAR_PATH`, pengaturan `API_CO_ID_*` Phase 1D | Manifest hari/sesi BEI lokal dan batas transport IDX. Rincian pengaturan universe dan freshness tercatat di [Phase 1D](phivealpha-phase-1d.md). |
| `PHIVEALPHA_DAILY_CANARY_DATE`, `PHIVEALPHA_DAILY_CANARY_APPROVAL_PATH` | Satu tanggal sesi tertutup dan approval canary terpisah dengan scope GET persis. |
| `PHIVEALPHA_R2DBC_URL`, `PHIVEALPHA_R2DBC_USERNAME`, `PHIVEALPHA_R2DBC_PASSWORD` | Hanya saat Phase 1C sengaja diaktifkan untuk database development terisolasi. |

Override Spring untuk FX adalah `phivealpha.api-co-id-fx.cache-ttl`, `max-age`, `max-retries` (0–2, default 0), dan `timeout`. Override TTL kalender nasional memakai `phivealpha.api-co-id-holiday.cache-ttl`. Startup opt-in hanya menyiapkan bean, tanpa polling/scheduler. Transport mematikan redirect, wiretap, detail logging codec, dan retry otomatis Reactor Netty. Percobaan ulang eksplisit dibatasi hanya untuk transport/timeout dan HTTP transient. Produk dengan grant Free kosong gagal sebelum HTTP.

## Kuota, restart dan penyimpanan

- Anggaran default per jendela vendor yang sudah ditinjau adalah IDX 350, FX 60, dan Holiday 12. Semuanya **batas internal**, sementara kuota akun vendor tetap `UNVERIFIED` sampai ada observasi terkonfirmasi. Opsional, isi `<PRODUCT>.vendor-quota-reference`, `<PRODUCT>.vendor-limit`, `<PRODUCT>.vendor-remaining` dan `<PRODUCT>.vendor-observed-at` untuk menampilkan bukti yang berlaku pada jendela aktif. Nilai vendor adalah observasi bertanggal, terpisah dari estimasi internal yang berjalan. Tidak ada penjadwalan reset tanggal 1 atau inferensi sisa kuota akun dari ledger.
- Manifest properties memakai `<PRODUCT>.start`, `<PRODUCT>.end`, `<PRODUCT>.reset-reference`, dan opsional `<PRODUCT>.limit` untuk `IDX`, `FX`, `HOLIDAY`. Start dan end berupa Instant UTC dari bukti reset aktual. Window adalah [start, end). Siapkan window yang bersebelahan atau tidak tumpang tindih secara manual setelah bukti reset baru. Window yang tumpang tindih dengan penggunaan terdahulu dan memakai batas waktu berbeda ditolak.
- Grant route membutuhkan `<DATASET>.free-confirmed=true`, `<DATASET>.entitlement-reference`, dan `<DATASET>.expires`. Dataset adalah `INSTRUMENTS`, `DAILY`, `MARKET_NEWS`, `SYMBOL_NEWS`, `USD_IDR`, atau `NATIONAL_HOLIDAYS`. `<DATASET>.contract-reference` mengizinkan penilaian FX verified jika syarat kualitas lain terpenuhi. Rujukan dan jalur file tidak keluar di endpoint status.
- Provision jurnal satu kali di direktori development privat dengan akses terbatas. Format awal satu baris `PHIVEALPHA-QUOTA-V1` plus newline. Gunakan file dan filesystem lokal yang sama di seluruh proses pemakai satu akun. Pertahankan jurnal saat upgrade/restart. Jurnal yang hilang, kosong, rusak atau tidak bisa ditulis menolak fetch. Jangan membuat ulang jurnal pada jendela aktif atau mengedit histori. Gunakan backup terkini dan koordinasi operator bila pemulihan diperlukan.
- Percobaan dialokasikan sebelum I/O, ditulis dengan lock OS dan `force(true)` di worker bounded elastic. Hasil akhir dicatat terpisah. Crash atau cancel setelah admission tidak mengembalikan budget. Retry dan halaman IDX berikutnya masing-masing mendapat satu admission. Cache hit dan request duplikat yang berbagi pekerjaan tidak menambah konsumsi. HTTP 401/402/403 menahan produk di jendela yang sama. HTTP 429 memakai throttle dan mencatat waktu tunggu durable dari Retry-After dengan minimum lokal 60 detik. Lock yang sedang dipakai proses lain menyebabkan admission gagal tertutup. Pemakaian aplikasi lain dan kuota vendor tetap membutuhkan pengecekan manual sebelum percobaan live.
- Jurnal hanya menyimpan tipe dataset, UUID, jendela dan timestamp percobaan, kode HTTP, dan kategori hasil. Dashboard menyajikan status mode, grant, budget internal, estimasi internal tersisa, error/percobaan/pending per dataset, freshness, provenance dan waktu terakhir data lolos parser pada proses ini. Statistik transport durable dibedakan dari snapshot normalisasi in-memory.
- Phase 1C tetap opt-in. Jika database development terisolasi telah disiapkan melalui migrasi manual Phase 1C, mirror metadata boleh diaktifkan dengan `phivealpha-persistence,phivealpha-api-co-id-audit` bersama profil adapter yang dipilih. Mirror memerlukan `<DATASET>.metadata-retention-reference` dan grant yang berlaku. Runtime memeriksa URL loopback, port eksplisit, serta database dan user khusus `phivealpha_dev_*`. Gunakan klaster development terpisah secara fisik dari production. Mirror mencatat metadata transport melalui `IdxPersistenceService.recordIngestion`. Kegagalan mirror tidak mengembalikan budget. Raw payload dan data market tidak dipersist sampai ada verifikasi hak retensi dan review terpisah.

## SIT offline

```bash
JDK_JAVA_OPTIONS="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar" \
  mvn -o -f backend/pom.xml clean verify
mvn -o -f backend/pom.xml -Pphivealpha -DskipTests package
(cd frontend && node test-market-data.mjs && npm run test:ai-status && npm run test:news && \
  npm run test:tutorial && npm run build && npm run build:phivealpha)
```

`ApiCoIdFreeIntegrationTest` memakai mock WebClient `ExchangeFunction`, fixture JSON, Clock terkontrol, dan file jurnal sementara. Untuk smoke dashboard di host SIT, aktifkan **hanya** `phivealpha-developer`, inject token privat, lalu akses `GET /internal/developer/api-co-id` dari loopback dengan header `X-PhiveAlpha-Developer-Token`. Token hilang atau asal non-loopback menghasilkan 404. Respons bertanda no-store. Jangan teruskan endpoint ke Nginx atau frontend publik. Mode yang diharapkan adalah IDX fixture, FX dan Holiday disabled, dan kuota vendor unverified. Pengujian integrasi PostgreSQL terpisah tersedia melalui [Phase 1C](phivealpha-phase-1c.md) setelah provision klaster SIT.

## Daily canary untuk tahap berikutnya

1. Konfirmasi tertulis tier Free untuk Daily BBCA, sisa akun dan waktu reset, hak metadata, tanggal sesi BEI yang telah tutup, kontrak harga/volume OHLCV, dan izin satu GET pada host development. Siapkan manifest dan jurnal durable di atas.
2. Siapkan approval privat terpisah dengan properti `scope`, `free-confirmed=true`, `entitlement-confirmed=true`, `single-request-approved=true`, `entitlement-reference`, `contract-reference`, `operator-approval-reference`, `expires-at`, dan `max-age-seconds`. Scope persis `GET https://use.api.co.id/idx/v2/chart/daily/BBCA?timeframe=1d&order=asc&from=YYYY-MM-DD&to=YYYY-MM-DD`, dengan tanggal yang sama dengan env dan kalender.
3. Pada tahap yang sudah disetujui, gunakan artefak dari packaging di atas dan jalankan sekali dari root repo. Jaga shell tracing dan Java agent tetap mati. Process budget, concurrency, max pages dan max rows masing-masing satu, retry nol. Cursor lanjutan membuat hasil incomplete. CLI tidak memuat Spring, database atau scheduler. Exit 2 menandai guard/config yang gagal, exit 1 hasil tanpa candle. Output hanya metadata aman. Hasil lolos parser masih dapat berstatus unverified atau stale.

```bash
env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS \
  java -Dloader.path=backend/target/test-classes \
    -Dloader.main=com.phivealpha.marketdata.apicoid.ApiCoIdDailyCanary \
    -cp backend/target/phivealpha-backend.jar \
    org.springframework.boot.loader.launch.PropertiesLauncher --confirm-live
```

Daily canary belum dijalankan. Quote canary Phase 1D.2 tetap entry point terpisah dan kini juga memerlukan grant `QUOTE` dan ledger IDX. Quote berada di luar scope Free dan bukan target pelaksanaan phase ini.

## File dan risiko terbuka

- Paket `marketdata/apicoid` menambah manifest akses, durable quota ledger, runtime profile, dua adapter macro, cache, diagnostics, endpoint developer, dan mirror metadata Phase 1C. Gate, transport, konfigurasi dan provider IDX existing menambah admission serta observability bersama.
- `ApiCoIdDailyCanary.java`, `ApiCoIdQuoteCanary.java` dan `ApiCoIdFreeIntegrationTest.java` menambah dan menguji guard developer serta mock produk. Current state dan session log mencatat handoff.
- Entitlement, hak retensi dan kuota vendor aktual tetap belum diketahui. Jurnal lokal memerlukan satu jalur bersama dan pengelolaan backup/rotasi yang terkontrol. Data Phase 1H tidak tersambung ke sinyal. Kalender IDX vendor masih audit-only sampai scope kontrak dan entitlement dapat dibuktikan.

## Hasil pengujian

Maven offline mencakup 288 kasus, dengan 287 lulus dan satu uji socket loopback Phase 1D.2 dilewati karena pembatasan sandbox. Enam belas kasus baru Phase 1H mencakup parser, timeout, HTTP 401/403/429/500, retry, duplikasi, cache versi, jendela kuota, exhaustion, restart JVM, guard canary, status dashboard, dan isolasi target audit. Uji URL dan status memakai fixture atau mock HTTP. Mirror PostgreSQL diuji pada guard konfigurasi. Koneksi PostgreSQL SIT belum dijalankan pada fase ini.

Kedua paket backend, kedua build produksi Angular, unit frontend market freshness (8), label AI (10), sumber berita (41), tutorial (41), dan regresi lifecycle request berita lulus. Build CoinMind masih menampilkan peringatan template lama. Tidak ada request ke gateway vendor, eksekusi canary, perubahan production atau PostgreSQL, commit, maupun push.
