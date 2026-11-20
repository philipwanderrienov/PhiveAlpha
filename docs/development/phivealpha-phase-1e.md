# PhiveAlpha Phase 1E — IDX Liquid Universe & Market Data Quality

Engine internal menilai saham biasa IDX dengan snapshot instrumen dan corporate action yang tersedia pada cutoff, kalender BEI berversi, serta candle EOD. Feed fixture, endpoint Weekly Top 5 demo, profil persistence, dan batas sinyal mengikuti fase sebelumnya.

## Kontrak service

`LiquidUniverseService.evaluate(reference, calendarReference, prices, cutoff)` adalah evaluasi sinkron atas input in-memory. Service tidak melakukan I/O. Caller reaktif dapat menyusun input terlebih dahulu lalu memanggil service. Tidak ada wiring ke provider, database, endpoint, job, atau UI baru.

- `Reference` memuat versi, `asOf`, `availableAt`, sumber, dan daftar anggota. Keanggotaan dan status instrumen berlaku pada `asOf`. Anggota membawa simbol, jenis efek, status listing, tanggal pencatatan, dan hasil review corporate action. Simpan versi baru ketika cakupan atau isi berubah.
- `CalendarReference` memuat manifest `VersionedIdxCalendar` dan waktu versi tersebut tersedia. Jadwal memakai Asia/Jakarta, termasuk libur, jeda sesi, dan jam khusus. Kalender harus mencakup setiap tanggal mundur dari tanggal cutoff sampai cukup sesi yang sudah tutup untuk lookback dan minimum usia IPO. Tanggal yang hilang membuat hasil `UNVERIFIED`.
- `DailyPrice` memakai kontrak Phase 1C. Turnover adalah nilai transaksi aktual IDR dan volume memakai lembar dalam cakupan pasar/sesi yang sama. `observedAt` harus pada atau setelah penutupan sesi. `ingestedAt` harus pada atau setelah `observedAt`. Keduanya wajib tersedia paling lambat pada cutoff. Candle untuk sesi berjalan dan data yang diketahui baru tersedia setelah cutoff dikeluarkan sebelum penghitungan. Koreksi yang datang terlambat tidak mengubah hasil pada cutoff lama.
- `Evaluation` membawa cutoff, versi/waktu/sumber referensi dan kalender, policy yang dipakai, masalah referensi, dan hasil menurut simbol. Setiap hasil membawa eligibility, metrik jika dapat dihitung, sesi yang diharapkan dan jumlah candle sah, indikasi evaluasi data, versi universe dan kalender, serta kode alasan dengan tanggal sesi bila relevan. Universe yang baru tersedia setelah cutoff menghasilkan masalah referensi dan daftar kosong agar keanggotaan masa depan tidak bocor.
- `LiquidCandidateRanking.rank(evaluation)` mengurutkan seluruh `ELIGIBLE` menurut median turnover turun, jumlah sesi likuid turun, sesi aktif turun, lalu simbol naik. Jumlah hasil dapat nol, kurang dari lima, atau lebih dari lima. Ranking mengukur likuiditas dan belum menjadi ranking return mingguan.

### Eligibility dan kualitas

| Eligibility | Arti |
| --- | --- |
| `ELIGIBLE` | Bukti instrumen lengkap, data lengkap dan fresh, semua batas likuiditas terpenuhi. |
| `INELIGIBLE` | Ada aturan yang gagal berdasarkan bukti tersedia, misalnya suspensi terkonfirmasi atau turnover di bawah batas. |
| `UNVERIFIED` | Bukti identitas, status, kalender, corporate action, atau data harga belum cukup untuk menetapkan kelayakan. |

Kualitas `COMPLETE` berarti seluruh sesi memiliki candle sah dengan basis dan sumber konsisten serta lolos freshness. `INCOMPLETE` mencakup candle hilang, duplikat, jadwal salah, atau isi/availability tidak valid. `STALE` mencakup sesi terakhir yang tertinggal dibanding sesi tertutup terbaru atau usia waktu penutupan terakhir yang melampaui kebijakan. `UNVERIFIED` menandai referensi/basis/provenance yang meragukan. `NOT_EVALUATED` berarti pemeriksaan instrumen sudah cukup untuk menolak tanpa membaca kualitas harga. Jika ada beberapa masalah harga, kesalahan struktur/duplikat didahulukan, lalu ketidakpastian basis atau sumber, lalu freshness dan kelengkapan. Seluruh alasan yang ditemukan pada tahap evaluasi harga tetap tercatat.

| Alasan | Pemicu |
| --- | --- |
| `REFERENCE_NOT_AVAILABLE_AT_CUTOFF`, `STALE_UNIVERSE_REFERENCE`, `DUPLICATE_INSTRUMENT` | Snapshot belum tersedia, as-of terlalu tua, atau simbol muncul lebih dari sekali setelah normalisasi. |
| `NOT_COMMON_STOCK`, `SUSPENDED`, `DELISTED`, `IPO_TOO_RECENT` | Jenis efek dikecualikan, status tidak aktif yang terkonfirmasi, atau sesi usia listing belum cukup. |
| `SECURITY_TYPE_UNVERIFIED`, `LISTING_STATUS_UNVERIFIED`, `LISTING_DATE_UNVERIFIED`, `CORPORATE_ACTION_UNVERIFIED` | Jenis/status/tanggal listing tidak diketahui atau tidak konsisten, atau corporate action belum jelas. |
| `CALENDAR_NOT_AVAILABLE_AT_CUTOFF`, `CALENDAR_OUT_OF_COVERAGE` | Versi kalender baru tersedia sesudah cutoff atau ada tanggal yang hilang. |
| `INVALID_CANDLE`, `NON_TRADING_CANDLE`, `DUPLICATE_CANDLE`, `MISSING_CANDLE` | OHLCV/turnover tidak valid, candle pada hari libur, ada lebih dari satu candle untuk sesi, atau tidak ada candle sah untuk sesi yang disyaratkan. |
| `INVALID_PRICE_AVAILABILITY`, `ADJUSTMENT_BASIS_UNVERIFIED`, `MIXED_PRICE_SOURCE`, `STALE_PRICE` | Timestamp belum lengkap/tidak konsisten, basis selain `UNADJUSTED`, sumber berubah dalam window, atau harga tertinggal/melewati batas usia. |
| `ACTIVE_SESSIONS_BELOW_MINIMUM`, `ACTIVE_RATIO_BELOW_MINIMUM`, `LIQUID_SESSIONS_BELOW_MINIMUM`, `MEDIAN_TURNOVER_BELOW_MINIMUM` | Data sah dan masih segar, tetapi salah satu threshold tidak terpenuhi. |

## Rumus dan konfigurasi

- Nilai transaksi harian adalah `DailyPrice.turnover` dari sumber yang mengagregasikan nilai eksekusi. `close × volume` tidak dipakai. Metrik menampilkan turnover dan volume pada sesi terakhir serta median turnover seluruh window. Untuk jumlah genap, median adalah rata-rata dua nilai tengah dengan `BigDecimal` eksak.
- Hari aktif memiliki turnover dan volume positif. Rasio aktif adalah hari aktif dibagi seluruh sesi dalam window. Output dibulatkan ke delapan desimal. Pemeriksaan threshold memakai perbandingan desimal tanpa pembulatan rasio. Hari tanpa transaksi hanya dihitung nol jika ada candle flat eksplisit dengan volume dan turnover nol. Missing candle tidak diisi nol.
- Sesi likuid memenuhi minimum turnover harian dan volume lembar pada sesi yang sama. Minimum hari aktif, rasio aktif, sesi likuid, dan median diperiksa terpisah. Batas minimum inklusif. Candle wajib lengkap pada seluruh window termasuk untuk IPO yang sudah melewati minimum usia.
- Usia harga diukur sejak penutupan candle sah terbaru. Kalender menemukan sesi tertutup terbaru tanpa menghitung hari libur. Batas umur wall-clock adalah pagar tambahan dan dapat terlampaui pada libur panjang. Usia referensi diukur sejak timestamp as-of.

Semua nilai awal adalah hipotesis untuk diuji dengan data historis point-in-time. Override melalui properti Spring di bawah prefix `phivealpha.liquid-universe`. Record `LiquidityPolicy` juga dapat diberikan langsung pada pengujian.

| Properti | Default | Arti |
| --- | ---: | --- |
| `lookback-sessions` | 20 | Jumlah sesi EOD lengkap yang diperiksa, 1–252. |
| `min-listing-sessions` | 20 | Minimum jumlah sesi tutup sejak listing, termasuk sesi pencatatan, 1–252. |
| `min-active-sessions` | 15 | Minimum hari yang bertransaksi. |
| `min-active-ratio` | 0,75 | Minimum fraksi sesi aktif. Gunakan titik desimal saat mengatur properti. |
| `min-liquid-sessions` | 12 | Minimum sesi yang memenuhi kedua ambang transaksi harian. |
| `min-daily-turnover-idr` | 1.000.000.000 | Turnover IDR per sesi untuk dihitung sebagai sesi likuid. |
| `min-median-turnover-idr` | 2.000.000.000 | Median turnover IDR minimum. |
| `min-daily-volume-shares` | 100.000 | Volume lembar per sesi untuk dihitung sebagai sesi likuid. |
| `max-price-age` | `7d` | Usia maksimum dari penutupan sesi candle terbaru. |
| `max-reference-age` | `7d` | Usia maksimum dari as-of referensi universe. |

## Risiko dan batas penggunaan

Snapshot harus mencakup universe yang dimaksud dan mempertahankan instrumen yang kemudian delisting untuk menghindari survivorship bias. Satu snapshot yang tersedia sebelum cutoff tidak membuktikan perubahan status di antara as-of dan cutoff. Caller perlu memilih versi yang mencakup pemberitahuan listing, suspensi, dan corporate action yang sudah diketahui, serta mempertahankan waktu publikasi versi kalender dan versi candle. Nilai `CLEAR` menyatakan review sebelum cutoff. Engine bergantung pada ketepatan bukti tersebut.

Candle adjusted, campuran vendor yang berubah, dan corporate action ambigu gagal tertutup. Penyatuan feed lintas basis memerlukan kontrak dan revisi historis yang dapat ditelusuri. Turnover dan volume perlu diverifikasi untuk papan, mekanisme perdagangan, dan unit yang konsisten. Min market cap, free float, risiko sektor, slippage, spread, dan dampak order belum dimodelkan. Label eligible hanya berlaku pada input dan policy evaluasi.

## File

- `screening/LiquidUniverseService.java`, `LiquidUniverse.java`, `LiquidityPolicy.java`, dan `LiquidCandidateRanking.java` menambah engine serta kontrak internal.
- `PhiveAlphaApplication.java` mengikat konfigurasi. `marketdata/apicoid/VersionedIdxCalendar.java` menyediakan timestamp tutup sesi yang dipakai bersama.
- `screening/LiquidUniverseServiceTest.java`, fixture `phivealpha/liquid-universe/calendar.json`, dan `IdxFoundationTest.java` menguji kontrak dan wiring fixture.

## Validasi offline

- Maven `clean verify` offline mencatat 258 kasus. Sebanyak 257 lulus, tanpa gagal atau error. Satu uji connector loopback Phase 1D.2 dilewati karena sandbox menolak socket. Tujuh tes baru Phase 1E lulus dan mencakup median ganjil/genap, volume dan turnover, ranking stabil dengan tiga hasil, status instrumen, cutoff, freshness, adjustment, duplikasi, missing candle dan cakupan kalender. Skenario memakai data sintetis.
- Rerun terarah `IdxFoundationTest,LiquidUniverseServiceTest` lulus 12 kasus, termasuk override property Spring dan regresi endpoint demo.
- Kedua paket backend lulus dan manifest memuat entry point masing-masing. Unit frontend lulus untuk market freshness (8), label AI (10), sumber berita (41), tutorial (41) dan regresi alur request berita. Kedua build produksi Angular lulus dengan peringatan template lama pada CoinMind.
- Phase 1E tidak memerlukan API key, panggilan vendor, database, scheduler atau lingkungan produksi. Validasi historis dengan data berizin masih diperlukan sebelum threshold dipakai untuk Dynamic Weekly Top 5.

```bash
JDK_JAVA_OPTIONS="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar" \
  mvn -o -f backend/pom.xml clean verify
mvn -o -f backend/pom.xml -Pphivealpha -DskipTests package
(cd frontend && npm run build && npm run build:phivealpha)
```
