# PhiveAlpha Phase 1K — Stock Screener Integration

`main` dan ref lokal `origin/main` terverifikasi pada `0d335b2` sebelum perubahan. Berkas untracked yang telah ada dipertahankan.

## Jalur evaluasi

`StockScreenerService.screenPersisted` menerima universe historis, kalender BEI berversi, snapshot berita dan flow per simbol, snapshot regime, serta cutoff. Reader Phase 1J mengambil candle unadjusted yang tersedia sampai cutoff dengan namespace data fixture dan versi kalender yang cocok. Caller menyediakan vintage yang tepat. Data ingestion, threshold likuiditas, dan model scoring memakai implementasi sebelumnya.

- `LiquidUniverseService` memilih sesi tertutup dan menilai status instrumen, corporate action, kecukupan OHLCV, turnover, aktivitas, serta freshness.
- Momentum memakai close sesi terbaru, lima sesi sebelumnya, dan sepuluh sesi sebelumnya. Seluruh 11 sesi harus lengkap, valid secara temporal, dan berasal dari sumber yang konsisten. Kode alasan dan tanggal tercatat pada `momentumIssue` bila faktor ini gagal.
- `NewsDrivenScoringService` menilai lima faktor dan memilih maksimal lima kandidat. Seri memakai simbol naik. Factor breakdown menyimpan bobot, kontribusi, metrik, kualitas, dan provenance. `decisions` mencatat kelayakan akhir dan hasil likuiditas masing-masing simbol. Faktor yang gagal membuat hasil akhir `UNVERIFIED` walaupun likuiditas lolos.
- Timestamp `generatedAt` menunjukkan waktu pembuatan snapshot. `dataAsOf` adalah penutupan sesi terakhir dari metrik likuiditas yang tersedia. `cutoff` adalah batas observabilitas. Kualitas snapshot merangkum masalah referensi dan keputusan. PostgreSQL yang gagal dibaca menghasilkan `UNAVAILABLE/HISTORICAL_STORAGE_UNAVAILABLE`, tanpa ranking atau fallback fixture.
- Harga yang diterima atau disimpan setelah cutoff dikeluarkan. Universe atau kalender yang belum tersedia tidak menghasilkan kandidat. Scoring mempertahankan gate first-seen dan waktu ketersediaan berita. Historical replay Phase 1G memakai vintage sendiri melalui kontrak yang sama dan tetap tercakup regresi.

## Endpoint read-only

`GET /api/idx/v1/stock-screener` menyajikan `FIXTURE` secara default. Fixture memakai kalender, universe, faktor, dan candle sintetis pada cutoff `2026-11-13T10:00:00Z`. Output scoring berlabel `RESEARCH_ONLY`. POST ditolak. Endpoint Weekly Top 5 lama tetap memiliki kontraknya sendiri.

Host development dapat mengaktifkan `phivealpha.stock-screener.persisted=true` dengan profil `phivealpha-persistence,phivealpha-daily-ingestion`. Mode `DEVELOPMENT_PERSISTENCE` pada endpoint memakai referensi fixture yang sama dan membaca candle yang sudah ditulis oleh pipeline Phase 1J. Opsi tanpa profil yang diperlukan menghasilkan `UNAVAILABLE/PERSISTENCE_DISABLED`. Universe historis lain disediakan melalui API internal service. Phase ini tidak menerbitkan sinyal atau membuka akses provider live.

## Validasi di phive-server

```bash
CI=true mvn -o -B -f backend/pom.xml clean verify
scripts/validation/phivealpha-persistence-it.sh --stock-screener -o
scripts/validation/phivealpha-persistence-it.sh --daily-ingestion -o
scripts/validation/phivealpha-persistence-it.sh --audit-mirror -o
git diff --check
```

Runner screener menyiapkan PostgreSQL 17 sementara, database dan role `phivealpha_dev_screener_it`, port loopback dinamis dan migrasi SQL 001/002. Tes memastikan identitas klaster sebelum menulis, memasukkan fixture melalui pipeline 1J, memeriksa cutoff, ranking service dan endpoint, alasan missing candle/suspensi, serta simulasi tabel history tidak tersedia. Di sandbox saat ini probe socket mendapat `EPERM` sebelum klaster dibuat. Eksekusi PostgreSQL R2DBC masih perlu dibuktikan di host yang mengizinkan loopback.

## Hasil lokal

Maven offline CI lulus dengan 94 tes lulus, satu skip pada connector loopback Phase 1D.2, dan nol gagal. Tes mencakup skor seri, batas lima, suspensi, corporate action, candle hilang/duplikat, freshness, price dan news masa depan, endpoint default, serta kegagalan R2DBC in-process tanpa fallback. `StockScreenerIT` terkompilasi dan eksekusi R2DBC aktual tersisa untuk host development. JAR hanya memuat kelas aplikasi PhiveAlpha. Shell syntax dan `git diff --check` lulus.

## File

- Backend `StockScreenerService.java`, `StockScreenerFixture.java`, `StockScreenerController.java`, `LiquidUniverseService.java`, dan `IdxDailyIngestionService.java`.
- Tes `StockScreenerServiceTest.java`, `StockScreenerStorageFailureTest.java`, `StockScreenerIT.java`, dan `IdxFoundationTest.java`.
- Runner `scripts/validation/phivealpha-persistence-it.sh`, status, log sesi, dan handoff ini.

Tidak ada perubahan skema atau frontend. Scope validasi tidak mencakup API live, perubahan produksi, order, deployment, commit, atau push.
