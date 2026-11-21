# PhiveAlpha SIT-02B — PostgreSQL Audit Mirror

SIT-02B menguji alur transport API.co.id hingga tabel `phivealpha_idx.idx_data_ingestion` di PostgreSQL 17 sementara. HTTP memakai `ExchangeFunction` in-process. Konteks Spring mengaktifkan profil persistence dan audit, sementara sumber data HTTP pengujian dibentuk terpisah dengan fixture grant Free dan izin retensi sintetis.

## Kontrak dan cakupan

- `ApiCoIdQuotaLedger` mencatat reservasi sebelum HTTP dan hasil transport sebelum mirror. Penghitungan budget dan blok ketika anggaran habis tetap berasal dari jurnal.
- HTTP 200 yang lolos parsing JSON menghasilkan `PARTIAL/SCHEMA_NOT_EVALUATED`. HTTP 404 menghasilkan `FAILED/HTTP_REJECTED`. Keduanya merekam provider, dataset `USD_IDR`, scope `TRANSPORT_ONLY`, waktu reservasi dalam presisi mikrodetik, jumlah record dan rejection nol, satu request per baris, batas budget, serta jendela yang sama dengan ledger.
- Tes membangun ulang transport dan akses quota, lalu membuka ulang jurnal yang sama. Dua baris audit dan isi jurnal harus tetap identik setelah rekonstruksi. Tidak ada replay mirror otomatis. Regresi Phase 1H juga mencakup pembacaan ledger dari JVM terpisah.
- PostgreSQL menolak insert ketiga dengan constraint `CHECK (false) NOT VALID` pada tabel sementara. Respons mock HTTP 200 tetap diterima, budget tersisa nol, dua baris yang sudah tersimpan tetap utuh, dan percobaan berikutnya ditolak sebelum HTTP. Tes mencocokkan UUID pada peringatan audit dengan reservasi yang gagal dimirror.
- `onErrorComplete()` mempertahankan mirror sebagai operasi best effort. Warning terstruktur memuat dataset, UUID reservasi, hasil transport, dan kelas error. Pesan exception, payload, kredensial, dan URL database tidak diteruskan. Baris SQL belum menyimpan UUID ledger. Korelasi SQL memakai dataset, waktu dan jendela. Kehilangan satu baris mirror setelah gagal tulis tetap mungkin dan tidak mengurangi pemakaian kuota.

## Menjalankan

```bash
mvn -o -B -f backend/pom.xml verify
scripts/validation/phivealpha-persistence-it.sh --audit-mirror -o
scripts/validation/phivealpha-persistence-it.sh -o
```

Mode audit membuat klaster di `/tmp/phivealpha-it.*/data` pada port loopback yang dipilih secara dinamis. Database dan role baru bernama `phivealpha_dev_audit_it`, dengan password acak. SQL administratif memakai Unix socket privat. Sebelum menulis, tes memeriksa direktori data, nama database, role, mayor PostgreSQL dan tabel audit. Mode runner lama tetap memakai `phivealpha_it` dan menjalankan empat kasus Phase 1C.

## File yang berubah

- `ApiCoIdAuditWriter.java` menambah konteks warning yang aman.
- `ApiCoIdAuditMirrorIT.java` memverifikasi kontrak audit dan ledger dengan PostgreSQL sementara.
- `ApiCoIdAuditWriterTest.java` memakai R2DBC connection factory in-process yang menolak koneksi untuk memverifikasi kegagalan mirror tanpa socket.
- `scripts/validation/phivealpha-persistence-it.sh` menambah mode `--audit-mirror` dan validasi PostgreSQL 17.
- `current-state.md`, `session-log.md`, dan handoff ini mencatat status.

## Validasi

- Maven offline `verify` lulus dengan 79 tes tercatat, 78 lulus dan 1 skip connector loopback Phase 1D.2. Regresi in-process membuktikan respons mock tetap berhasil, hanya satu percobaan storage dan HTTP terjadi, jurnal mencatat pemakaian, permintaan berikutnya ditolak sebelum HTTP, serta warning aman terlihat. `bash -n` dan `git diff --check` lulus.
- PostgreSQL 17.11 single-user berhasil menjalankan schema IDX, menyimpan metadata sintetis, dan membuktikan bahwa constraint menolak insert berikutnya sambil mempertahankan baris awal. Pemeriksaan SQL ini berjalan tanpa R2DBC.
- SIT-02B dan runner regresi Phase 1C terhalang izin pembuatan socket loopback dengan `EPERM` sebelum klaster dibuat. Kebijakan otomatis menolak eksekusi runner di luar sandbox. Validasi end-to-end PostgreSQL R2DBC masih menunggu hasil dari host development yang mengizinkan loopback.
- Default fixture, guard target development dan entitlement Free, larangan order, serta perubahan Phase 1H dan 1I dipertahankan. Seluruh tes mock berjalan tanpa request live. Tidak ada deployment, koneksi atau perubahan produksi, commit, atau push.
