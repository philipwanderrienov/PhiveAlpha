# PhiveAlpha Phase 1J — IDX Daily Market Data Pipeline

Baseline lokal `main` dan `origin/main` sama-sama menunjuk `3f8940f`. Perubahan untracked yang sudah ada dipertahankan, termasuk berkas pemulihan CoinMind.

## Jalur internal

`IdxDailyIngestionService.ingest` menerima feed, simbol, rentang inklusif, kalender berversi dengan waktu ketersediaan, dan versi universe. `FixtureDailyOhlcvFeed` menyediakan dua candle BBCA sintetis, satu hari libur, dan satu sesi khusus dengan waktu referensi deterministik. Service persistence pipeline memerlukan profil `phivealpha-persistence,phivealpha-daily-ingestion`. Guard yang juga dipakai SIT-02B mensyaratkan URL PostgreSQL loopback dengan port eksplisit serta database dan role `phivealpha_dev_*`. Caller internal memicu ingestion secara eksplisit.

- Pipeline memeriksa coverage kalender, status penutupan setiap sesi, rentang maksimum 366 hari, simbol, duplikasi dan kelengkapan candle sebelum menulis. Batch dengan pagination belum lengkap, data kosong, atau candle hilang ditolak seluruhnya pada tahap validasi.
- Waktu pasar diperoleh dari penutupan sesi pada kalender berversi. `asOf`, waktu observasi provider, dan freshness anchor diperiksa sebelum normalisasi. Waktu mulai penulisan tiap candle disimpan bersama waktu observasi, dibulatkan naik ke mikrodetik agar cutoff tidak melihat data terlalu dini. Identitas sumber menyimpan provider fixture, versi kalender dan versi universe awal. Timestamp fixture bersifat sintetis dan terikat pada tanggal pengujian. Runner memakai clock tetap. Fixture lama dapat berstatus `STALE` apabila dijalankan dengan clock sistem di luar batas freshness.
- Basis harga wajib eksplisit dan konsisten dalam satu batch. Turnover yang tidak tersedia disimpan sebagai `NULL` setelah SQL 002. Liquidity screening membutuhkan turnover aktual sehingga candle tersebut tidak lolos penilaian. Nilai OHLC, volume dan turnover yang tidak konsisten ditolak.
- Insert memakai kunci symbol, basis, dan tanggal. Retry yang nilainya identik menghasilkan `ALREADY_EXISTS`. Perubahan pada baris historis menghasilkan `REJECTED`. Penulisan berhasil menghasilkan `INSERTED` dan kegagalan database menghasilkan `FAILED`. Pada kegagalan parsial, baris yang berhasil tetap tersedia untuk retry. Audit batch fixture memakai penggunaan kuota nol dan melaporkan keberhasilan audit secara terpisah.
- Adapter HTTP Daily tetap memakai transport, entitlement dan ledger existing. `UNVERIFIED`, `STALE`, `INCOMPLETE` dan `UNAVAILABLE` diteruskan tanpa menyimpan candle. Phase 1J hanya menerima sumber fixture untuk write. Hak retensi API.co.id belum dianggap terverifikasi. Tidak ada penyimpanan raw payload, perubahan quota ledger, atau perubahan mirror metadata HTTP.
- `evaluate` membaca harga unadjusted menurut lookback sesi dari `LiquidUniverseService`, cutoff, dan sumber/versi kalender yang cocok. Versi universe saat ingestion tetap tersimpan pada baris awal. Evaluasi memakai universe historis yang disediakan caller agar keanggotaan saat ini tidak menentukan hasil replay. Screening tetap menerapkan versi referensi, corporate-action eligibility, freshness, coverage, dan cutoff. Revisi kalender yang versinya berbeda memerlukan review data sebelum hasil dapat digunakan.

## Validasi host development

```bash
CI=true mvn -o -B -f backend/pom.xml clean verify
scripts/validation/phivealpha-persistence-it.sh --daily-ingestion -o
scripts/validation/phivealpha-persistence-it.sh --audit-mirror -o
scripts/validation/phivealpha-persistence-it.sh -o
git diff --check
```

Runner `--daily-ingestion` memakai PostgreSQL 17 sementara dengan database dan role baru `phivealpha_dev_daily_it`, password acak, port loopback dinamis, dan administrasi SQL melalui Unix socket privat. Runner menerapkan SQL 001 dan 002. Test memverifikasi direktori data, database, role, dan versi server sebelum menulis. Skenario memaksa PostgreSQL menolak candle kedua, menghapus constraint sintetis, mengulang ingestion, dan membaca history sebelum serta sesudah cutoff. Mode lama dan mode SIT-02B tetap memakai SQL 001.

Maven offline pada mode CI lulus dengan 86 tes, satu skip connector loopback dan nol gagal. JAR diperiksa untuk memastikan hanya kelas aplikasi PhiveAlpha serta resource IDX yang masuk distribusi. Shell syntax dan `git diff --check` lulus.

PostgreSQL 17.11 single-user berhasil menerapkan SQL 001/002, menyimpan turnover `NULL`, mempertahankan satu baris setelah insert ulang, dan menolak visibilitas sebelum waktu ingestion. Runner R2DBC tertahan `EPERM` saat probe socket sebelum initdb. Pembuktian pipeline terhadap PostgreSQL melalui R2DBC masih membutuhkan host development.

## Inventaris perubahan

- Pipeline, fixture, guard, dan schema di `backend/src/main/java/com/phivealpha/persistence/` dan `backend/src/main/resources/phivealpha/sql/002-daily-optional-turnover.sql`.
- Integrasi lookback di `LiquidUniverseService.java`, kontrak status di `ApiCoIdData.java`, dan pemakaian guard bersama di `ApiCoIdAuditWriter.java`.
- Tes `IdxDailyIngestionTest.java`, `IdxDailyIngestionIT.java`, regresi quota di `ApiCoIdFreeIntegrationTest.java`, assertion fixture default di `IdxFoundationTest.java`, serta switch fail-closed di `ApiCoIdQuoteCanary.java`.
- Mode runner baru di `scripts/validation/phivealpha-persistence-it.sh`, status dan log sesi, serta handoff ini.

Frontend dan strategi trading tidak diubah. Seluruh validasi memakai fixture, mock, atau database sementara. Tidak ada koneksi produksi, request vendor live, transaksi, deployment, commit atau push.
