# PhiveAlpha Phase 1D.2

Canary developer memanggil `ApiCoIdHttpProvider.marketQuote("BBCA")` satu kali. Entry point dikompilasi ke `backend/target/test-classes` dan dijalankan langsung dalam JVM terisolasi. Kedua JAR aplikasi tetap memakai entry point masing-masing. Harness tidak membuat konteks Spring, memuat profil aplikasi, membuka database, menjalankan scheduler atau polling, ataupun menerbitkan sinyal.

## Persetujuan sebelum satu request live

1. Minta persetujuan eksplisit dari pemilik akun dan operator untuk satu GET quote BBCA pada akun, key, host development, dan waktu yang ditentukan. Konfirmasi entitlement dan tarif rute secara tertulis serta cek sisa hit, rate limit, dan pemakaian bersama di dashboard vendor. Ringkasan publik menempatkan quote mulai paket Starter. Dokumentasi sandbox mencantumkan keluarga `/idx/*` untuk data mock. Pastikan jenis key yang dipakai sesuai tujuan satu request aktual. [Produk IDX API.co.id](https://api.co.id/api-saham-indonesia/) · [Sandbox vendor](https://docs.api.co.id/sandbox/)
2. Konfirmasi hak akses internal/non-display, diagnosis, retensi metadata, serta rujukan berizin untuk membandingkan hasil. Review kontrak envelope, identifier, IDR, unit volume, timezone, `as_of`, lag, dan nullability. Vendor menyebut field intraday dapat kosong di luar sesi. Setujui ambang freshness dan pilih window perdagangan dari manifest BEI yang telah ditinjau. [Kontrak IDX](https://docs.api.co.id/products/idx-market-data/) · [Produk IDX API.co.id](https://api.co.id/api-saham-indonesia/) · [Jam perdagangan BEI](https://www.idx.co.id/id/produk/mekanisme-dan-jam-perdagangan)
3. Jalankan uji connector loopback pada host development yang mengizinkan socket. Pastikan JVM tepercaya dan host terisolasi dari production. Izinkan HTTPS langsung hanya ke gateway resmi untuk jendela canary. Siapkan anggaran satu proses dan jangan menjalankan proses paralel.
4. Simpan persetujuan lokal dengan akses terbatas, tanpa kredensial, di luar repo. Format berikut memakai nilai contoh. Operator mengisi referensi yang menunjuk pada bukti aktual setelah persetujuan diberikan. Harness memvalidasi deklarasi lokal dan masa berlaku secara offline. Pemeriksaan bukti dan kecocokan key dengan akun yang disetujui dilakukan operator.

```properties
scope=GET https://use.api.co.id/idx/v2/chart/quote/BBCA
entitlement-confirmed=true
single-request-approved=true
entitlement-reference=REPLACE_WITH_VENDOR_APPROVAL_REFERENCE
contract-reference=REPLACE_WITH_REVIEWED_CONTRACT_REFERENCE
operator-approval-reference=REPLACE_WITH_ONE_REQUEST_APPROVAL_REFERENCE
expires-at=REPLACE_WITH_APPROVED_UTC_INSTANT
max-age-seconds=REPLACE_WITH_APPROVED_INTEGER_SECONDS
```

## Menjalankan canary

Dari root repo pada host development, dengan JDK 25, bangun artefak developer sebelum menyuntikkan key.

```bash
mvn -o -f backend/pom.xml -Pphivealpha -DskipTests package
```

Atur `PHIVEALPHA_CANARY_APPROVAL_PATH` dan `API_CO_ID_CALENDAR_PATH` ke path absolut file lokal yang sudah ditinjau. Suntikkan `API_CO_ID_KEY` via environment privat untuk proses ini saja, misalnya dengan secret manager. Jaga shell tracing dan perekaman environment tetap mati. Gunakan direktori dan artefak build tepercaya.

```bash
# Tahap live berikutnya, setelah semua persetujuan diterima
env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS \
  java -Dloader.path=backend/target/test-classes \
    -Dloader.main=com.phivealpha.marketdata.apicoid.ApiCoIdQuoteCanary \
    -cp backend/target/phivealpha-backend.jar \
    org.springframework.boot.loader.launch.PropertiesLauncher --confirm-live
```

Tanpa flag, approval yang lengkap dan belum kedaluwarsa, kalender valid, serta key yang valid secara format, harness berhenti sebelum HTTP. Kehadiran marker CI umum memblokir harness, termasuk `CI=false`. Jangan hapus marker tersebut atau menjadwalkan CLI di pipeline. Origin, dataset dan simbol terkunci. Budget, concurrency, dan max pages masing-masing 1. Retry 0. Timeout request 8 detik, deadline operasi 10 detik, buffer respons maksimum 1 MiB. Pengaturan profil aplikasi tidak dimuat.

Bandingkan satu admission dengan dashboard akun setelah panggilan dan tutup jendela egress. Setiap proses baru memiliki budget lokal baru. Budget lokal hanya mencatat percobaan yang diizinkan oleh gate, termasuk kegagalan transport. Jumlah hit yang ditagih dan sisa kuota akun harus diperiksa di dashboard. Bahkan bila hasilnya timeout, proses mungkin sudah mengirim request. Jangan ulangi tanpa persetujuan baru. Cabut atau rotasi key canary sesuai prosedur akun.

## Laporan dan transport

Laporan JSON dibatasi ke dataset, simbol, kode HTTP bila diterima, elapsed time dalam milidetik hingga hasil pipeline, jumlah admission, status parser, indikator keberadaan field, waktu `as_of`, observasi dan fetch, usia konservatif, tanggal sesi, provenance provider/manifest, kualitas dan alasan internal. Usia mengikuti timestamp tertua dari `as_of` dan waktu fetch dikurangi `stale_seconds` jika tersedia. Map field kosong berarti tidak ada JSON yang bisa diperiksa. Boolean field menunjukkan kehadiran non-null. Parser memeriksa tipe, rentang dan konsistensi. Metadata waktu yang hanya dapat diambil dari snapshot tervalidasi bernilai null ketika parsing gagal.

| Hasil | Arti |
| --- | --- |
| `UNVERIFIED` | Parser menerima quote dan tahap review kontrak data aktual masih terbuka. |
| `STALE` | Usia melampaui ambang yang disetujui atau kalender menunjukkan sesi yang lebih baru. |
| `INCOMPLETE` | Parser menolak JSON, envelope, field, atau konsistensi quote. |
| `UNAVAILABLE` | HTTP gagal, koneksi gagal, atau deadline habis. |
| `VERIFIED` | Memerlukan entitlement dan kecocokan kontrak respons aktual dengan referensi yang berizin. Phase 1D.2 belum memiliki jalur yang menerbitkan status ini. |

Exit 0 untuk hasil quote yang lolos parser, termasuk `STALE` dan `UNVERIFIED`. Exit 1 untuk `INCOMPLETE` atau `UNAVAILABLE`. Exit 2 untuk penolakan guard, konfigurasi atau kegagalan lokal tak terduga. Baca kualitas JSON sebelum mengambil keputusan.

Vendor memakai REST langsung tanpa SDK wajib. Reactor Netty menyediakan wiretap yang dapat mencatat bytes HTTP dan WebClient menyediakan logging detail request. Harness tidak mengaktifkan wiretap, secara eksplisit mematikan logging detail codec, dan menonaktifkan logger di JVM khusus ini. Output tidak menyertakan URL request, credential, header, body, nilai quote, atau cursor. Reactor Netty dikonfigurasi dengan `followRedirect(false)` dan `disableRetry(true)`. Retry adapter juga nol. Reactor Netty mendokumentasikan satu retry default untuk abort tingkat TCP saat retry library diizinkan. Perilaku internal gateway, termasuk replay atau retry di sisi vendor, tidak diketahui dari dokumentasi publik. Hindari proxy, gateway tambahan, dan Java agent yang dapat mencatat atau mengulang request. [Kontrak REST vendor](https://api.co.id/api-saham-indonesia/) · [Referensi Reactor Netty HTTP Client](https://projectreactor.io/docs/netty/release/reference/http-client.html)

## Audit cakupan berikutnya

- **Universe `index=IHSG`.** Halaman vendor membedakan daftar instrumen dan daftar konstituen indeks. Cakupan filter aktual pada akun belum terbukti. Buat daftar referensi bertanggal dari universe yang disetujui, definisikan cakupan papan dan jenis efek serta perlakuan atas IPO, suspensi, dan delisting, lalu cocokkan himpunan simbol setelah pagination lengkap. Adapter Phase 1D sudah menolak master tanpa referensi lokal dan memeriksa kesamaan himpunan. [Daftar endpoint vendor](https://api.co.id/api-saham-indonesia/) · [Katalog data BEI](https://www.idx.id/media/2qalvu4z/20240923_idx-data-catalogue-pricelist-2024.pdf)
- **Pagination instrument master.** Limit yang diminta dapat dipotong batas vendor. Cursor dapat berulang, kedaluwarsa, atau bergeser saat universe berubah. Perlu konfirmasi semantik terminasi/`count`, konsistensi snapshot lintas halaman, batas page size, dan biaya setiap halaman. Phase 1D menggagalkan batch parsial pada limit, duplikasi, cursor atau terminasi tidak valid. Canary ini khusus quote dan tidak menghabiskan budget untuk master. [Metadata respons vendor](https://api.co.id/api-saham-indonesia/) · [Handoff Phase 1D](phivealpha-phase-1d.md)
- **Adjustment OHLCV.** Parser daily Phase 1D memakai `ADJUSTED` berdasarkan dokumentasi split/bonus yang ditelaah pada fase tersebut. Verifikasi faktor dan basis harga/volume pada tanggal aksi, penanganan dividen/rights, revisi dan versi adjustment sebelum backtest. Quote tetap memakai basis `UNKNOWN`. Dokumentasi detail IDX gagal dimuat ulang saat audit ini. [Kontrak IDX](https://docs.api.co.id/products/idx-market-data/) · [Handoff Phase 1D](phivealpha-phase-1d.md)
- **Riwayat berita.** Vendor mendeskripsikan feed pasar dan per simbol. Retensi, kedalaman pagination, cakupan akun, backfill, revisi, dan timestamp pertama tersedia belum terbukti. Waktu publikasi dan waktu observasi fetch dipisahkan oleh adapter. Historical replay perlu bukti first-seen point-in-time dan hak penggunaan judul/tautan. [Feed berita vendor](https://api.co.id/api-saham-indonesia/) · [Handoff Phase 1D](phivealpha-phase-1d.md)

Fixture dan kelayakan BUY mengikuti Phase 1D. Perubahan kualitas untuk screening, persistensi, atau tampilan memerlukan review dan persetujuan tersendiri.

## Validasi offline

- Maven `clean verify` offline dengan Mockito Java agent mencatat 251 kasus, 250 lulus dan 1 dilewati, tanpa gagal atau error. Canary mencakup guard, expiry dan scope entitlement, status HTTP, usia, field hilang, JSON rusak, cursor, dan redaksi melalui HTTP exchange mock in-process. Uji terhadap server HTTP loopback disertakan untuk 302, 503 dan putus koneksi. Sandbox menolak pembuatan socket dengan `Operation not permitted`, sehingga perilaku connector pada wire belum terverifikasi di host ini.
- Paket backend CoinMind dan PhiveAlpha serta kedua build produksi Angular lulus. Unit frontend lulus untuk market freshness (8), label AI (10), sumber berita (41), tutorial (41), dan regresi alur request berita. Build CoinMind menampilkan peringatan template yang sudah ada.
- Smoke test launcher mengembalikan exit 2 untuk flag hilang, marker CI, dan approval hilang. Manifest kedua JAR menunjuk ke entry point aplikasi masing-masing dan kelas CLI hanya ada di test classes.
- Live canary belum dijalankan. Tidak ada request API.co.id atau Gemini, perubahan produksi, commit, atau push.

Untuk mengulang uji canary offline pada host development yang mengizinkan loopback, jalankan `mvn -o -f backend/pom.xml -Dtest=ApiCoIdQuoteCanaryTest test`. Uji menggunakan key sintetis dan server di `127.0.0.1`. Pada lingkungan yang memblokir Mockito self-attach, perintah suite penuh memakai `JDK_JAVA_OPTIONS="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar"`.
