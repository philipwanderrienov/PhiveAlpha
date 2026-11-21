# PhiveAlpha Phase 1C

Phase 1C menambahkan penyimpanan PostgreSQL untuk domain IDX di Spring Boot 4.1.1, Java 25, WebFlux dan Spring Data R2DBC. Endpoint Phase 1A dan layar Angular tetap menggunakan provider fixture. Data contoh memakai provenance sintetis.

## Inventaris perubahan

| Lokasi | Perubahan |
| --- | --- |
| `backend/src/main/java/com/phivealpha/persistence/IdxRecords.java` | Model instrumen, harga harian, berita dan ingestion. |
| `backend/src/main/java/com/phivealpha/persistence/IdxRepository.java` | SQL berparameter dan pemetaan reaktif melalui `R2dbcEntityTemplate`. |
| `backend/src/main/java/com/phivealpha/persistence/IdxPersistenceService.java` | Validasi, idempotensi, pembacaan as-of, transaksi berita, timeout dan error. |
| `backend/src/main/java/com/phivealpha/persistence/IdxPersistenceConfiguration.java` | Koneksi PostgreSQL khusus profil, template dan transaction manager. |
| `backend/src/main/java/com/phivealpha/persistence/IdxMarketCalendar.java` | Kontrak sesi BEI dengan zona Asia/Jakarta. |
| `backend/src/main/resources/phivealpha.yml` | Konfigurasi koneksi opt-in. |
| `backend/src/main/resources/phivealpha/sql/001-idx.sql` | Migrasi offline untuk schema `phivealpha_idx`. |
| `backend/src/test/java/com/phivealpha/IdxFoundationTest.java` | Memastikan service persistence absen di fixture. |
| `backend/src/test/java/com/phivealpha/persistence/{IdxPersistenceValidationTest,IdxPersistenceProfileTest,IdxPersistenceIT}.java` | Validasi input, pengujian konteks, serta integration test terisolasi. |
| `scripts/validation/phivealpha-persistence-it.sh` | Runner PostgreSQL sementara dengan direktori data privat, socket privat, port loopback dan password acak. |
| `docs/development/{current-state,session-log,phivealpha-phase-1c}.md` | Status dan handoff. |

## Struktur data

| Tabel | Kunci dan relasi | Fungsi |
| --- | --- | --- |
| `idx_instruments` | PK `symbol` | Identitas, sektor, tipe, status listing, mata uang, ukuran lot, timestamp pembaruan. Update menerima observasi yang lebih baru. |
| `idx_daily_prices` | PK `(symbol, adjustment_status, trading_date)`, FK ke instrumen | OHLC dan turnover sebagai `numeric`/`BigDecimal`, volume sebagai `bigint`. PK mendukung pencarian rentang per simbol dan basis. Baris pertama untuk sebuah kunci dipertahankan. |
| `idx_news` | PK `article_id`, unique `content_hash` | Metadata kanonis, provenance, dan status validasi. Indeks parsial untuk waktu tersedia berita tervalidasi. |
| `idx_news_aliases` | PK `article_id`, FK `canonical_id` ke berita | ID dari provider lain dapat mengarah ke berita dengan hash sama. Penggunaan ulang ID dengan hash berbeda ditolak. |
| `idx_news_symbols` | PK `(article_id, symbol)`, FK ke berita dan instrumen | Satu artikel dapat terkait ke beberapa emiten. Indeks `(symbol, available_at, article_id)` mendukung lookup as-of. |
| `idx_data_ingestion` | PK `ingestion_id` | Audit provider/dataset/scope, waktu fetch, status, kategori error, jumlah record dan penolakan, pemakaian dan batas request serta jendela budget. |

Semua timestamp kejadian memakai `timestamptz` dan `OffsetDateTime`. Format tampilan lokal dapat diperoleh dengan konversi ke `IdxMarketCalendar.ZONE`. `trading_date` adalah tanggal sesi Asia/Jakarta. Caller harus membawa kalender BEI yang mengonfirmasi sesi sebelum menulis harga. Kalender tanpa data harus menolak tanggal yang belum terkonfirmasi. Basis harga `UNKNOWN` tersedia saat sumber belum menjelaskan penyesuaian. Caller hanya menetapkan `ADJUSTED` jika ada bukti dari sumber.

Untuk berita, urutannya adalah `published_at ≤ first_seen_at ≤ ingested_at ≤ available_at`. `available_at` harus mencerminkan kapan konten, validasi dan provenance benar-benar dapat digunakan. Riwayat harga memfilter `ingested_at` pada cutoff yang sama. Query berita hanya mengambil status `VALIDATED` dan memeriksa cutoff artikel serta relasi emiten. Duplikat mempertahankan metadata dan waktu kanonis pertama. Tautan baru membawa waktu ketersediaan dari observasi yang menambahkan tautan. Timestamp tulis harus tepat sampai mikrodetik atau lebih kasar. Cutoff dibulatkan turun ke mikrodetik.

Tulis instrumen, harga dan audit masing-masing atomik dalam satu statement. Berita, alias dan relasi ditulis dalam satu transaksi reaktif. Catat audit kegagalan sebagai panggilan tersendiri setelah percobaan ingestion berakhir. Driver memiliki connect timeout tiga detik. Operasi service memiliki deadline lima detik dan kesalahan storage dilaporkan melalui `PersistenceException` dengan cause untuk diagnosis internal. Validasi input dan konflik identitas memakai `IllegalArgumentException`.

## Migrasi manual dan rollback

Gunakan cluster PostgreSQL development terpisah dari CoinMind. Provision database dan akun development hanya pada cluster tersebut. Tinjau DDL di bawah sebelum menjalankannya. Periksa host/socket, database, user dan direktori cluster terhadap inventaris development sebelum memberi izin DDL. Untuk akun aplikasi, berikan akses hanya ke schema IDX dan izin DML yang diperlukan. [SQL 001](../../backend/src/main/resources/phivealpha/sql/001-idx.sql)

```bash
# Isi IDX_DEV_SOCKET, IDX_DEV_PORT, IDX_DEV_DB dan IDX_DEV_MIGRATOR dari cluster development.
idx_psql=(psql -X -v ON_ERROR_STOP=1 -h "${IDX_DEV_SOCKET:?}" -p "${IDX_DEV_PORT:?}" \
  -d "${IDX_DEV_DB:?}" -U "${IDX_DEV_MIGRATOR:?}")
"${idx_psql[@]}" -c 'SELECT current_database(), current_user' -c 'SHOW data_directory'
# Setelah target development dikonfirmasi dan SQL ditinjau
"${idx_psql[@]}" -f backend/src/main/resources/phivealpha/sql/001-idx.sql
"${idx_psql[@]}" -c '\dt phivealpha_idx.*'
```

SQL 001 membuka transaksi eksplisit dan membuat schema baru. Kegagalan sebelum commit menggulung kembali DDL. Untuk rollback setelah commit pada cluster development yang sudah dikonfirmasi, hentikan pemakai schema tersebut dan ekspor data IDX yang masih diperlukan. Jalankan SQL berikut dalam sesi `psql` development dengan `ON_ERROR_STOP=1`. Rollback menghapus data IDX yang tercantum. Jika schema berisi objek lain, `DROP SCHEMA` akan gagal dan transaksi dapat dibatalkan.

```sql
BEGIN;
DROP TABLE phivealpha_idx.idx_news_symbols, phivealpha_idx.idx_news_aliases,
    phivealpha_idx.idx_news, phivealpha_idx.idx_daily_prices,
    phivealpha_idx.idx_instruments, phivealpha_idx.idx_data_ingestion;
DROP SCHEMA phivealpha_idx;
COMMIT;
```

## Menjalankan development

```bash
mvn -f backend/pom.xml -DskipTests package
java -jar backend/target/phivealpha/phivealpha-backend.jar
# Pada terminal kedua, gunakan Node 22
cd frontend
npm start
```

Mode fixture membuka backend loopback port 8182. Untuk mengaktifkan persistence, set `PHIVEALPHA_R2DBC_URL` (format `r2dbc:postgresql://HOST:PORT/DB`), `PHIVEALPHA_R2DBC_USERNAME`, dan `PHIVEALPHA_R2DBC_PASSWORD` dari konfigurasi privat development. Setelah migrasi manual, jalankan `java -jar backend/target/phivealpha/phivealpha-backend.jar --spring.profiles.active=phivealpha-persistence`. Spring SQL initialization tetap `never`. Bean database tersedia untuk caller internal dan endpoint fixture tetap memakai fixture.

## Verifikasi

| Pemeriksaan | Hasil |
| --- | --- |
| `mvn -f backend/pom.xml test` | **PASS** dengan Mockito Java agent. 220 tes lulus, 0 gagal/error/skip. Termasuk 4 unit validasi IDX, 5 tes fixture dan endpoint, serta 1 tes konteks profil. Percobaan tanpa agent gagal karena sandbox menolak self-attach Mockito. |
| `mvn -f backend/pom.xml -DskipTests package` | **PASS**. |
| `mvn -f backend/pom.xml -DskipTests package` | **PASS** untuk CoinMind. |
| `npm run build:phivealpha` dan `npm run build` | **PASS** dengan Node 22. CoinMind masih menghasilkan peringatan template yang sudah ada. |
| DDL dan rollback PostgreSQL 17.11 | **PASS** dalam mode single-user dengan direktori sementara di `/tmp`, tanpa socket. DDL menghasilkan 6 tabel. Rollback menghapus schema. |

Perintah suite Maven yang lulus di sandbox:

```bash
JDK_JAVA_OPTIONS="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar" \
  mvn -f backend/pom.xml test
```

Integration PostgreSQL **NOT RUN**. Sandbox menolak pembuatan socket TCP loopback sebelum runner membuat direktori data atau menjalankan PostgreSQL. Jalankan `scripts/validation/phivealpha-persistence-it.sh -o` pada host development dengan izin loopback, Python 3, Maven, Java 25 dan binari PostgreSQL 17. `PHIVEALPHA_TEST_PG_BIN` dapat menunjuk direktori binari lokal. Runner menerapkan DDL melalui Unix socket privat, lalu tes memverifikasi `data_directory` sebelum write. Kasusnya mencakup upsert/retry, constraint harga unik, presisi NUMERIC, sorting dan cutoff harga, alias/hash, cutoff artikel/emiten, rollback FK dan audit ingestion.

Smoke browser **NOT RUN**. Sandbox menghentikan Chromium saat peluncuran dengan `Operation not permitted`. Endpoint fixture diuji lewat `WebTestClient` dalam proses.

## Risiko dan langkah Phase 1D

- Sediakan kalender resmi BEI dengan cakupan eksplisit, sesi khusus dan revisi hari libur. Hubungkan kalender tersebut ke adapter ingestion.
- Tetapkan definisi hash konten dan bukti timestamp per provider. Validasi ulang berita, koreksi harga dan versi adjustment/corporate action memerlukan model versi bertimestamp agar histori as-of tetap dapat diaudit. Saat ini metadata berita kanonis, tautan dan harga bersifat first-write.
- Jalankan integration PostgreSQL dan smoke browser di lingkungan yang mendukung loopback. Ukur pagination/backpressure, query plan dan kebutuhan pool sebelum menghubungkan workload ingestion atau screening persisten.
