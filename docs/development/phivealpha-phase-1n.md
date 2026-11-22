# PhiveAlpha Phase 1N — UX-first product redesign

Baseline lokal `main` pada `c614812`, di atas perubahan Phase 1L dan Phase 1M yang sudah ada di worktree.

## Arsitektur informasi

| Tahap | Pertanyaan pengguna | Tampilan |
| --- | --- | --- |
| Temukan | Ada saham yang layak saya teliti? | Overview mengarahkan ke Screener. Screener menampilkan jumlah kandidat, mutu dan waktu data, serta maksimal lima kartu dalam urutan API. |
| Bandingkan | Mana yang ingin saya lihat lebih dulu? | Setiap kartu memakai posisi, kode dan nama emiten, serta skor hanya ketika mutu mengizinkan. Weekly Top 5 membandingkan perubahan harga dalam periode yang sama. |
| Pahami | Bukti apa yang membuat saham ini masuk peringkat? | **Lihat riset** membuka tampilan satu saham dengan penjelasan likuiditas, momentum, katalis berita, arus, dan risiko pasar. |
| Selidiki | Nilai dan sumber mana yang mendasari penjelasan? | Rincian faktor dan sumber memakai disclosure native tertutup saat pertama dibuka. Stock Analysis menampilkan harga, perubahan mingguan, dan sesi. News Intelligence memberi konteks publikasi dan tautan ke emiten. |

Saham yang belum lolos berada di bawah kandidat, dengan penjelasan Indonesia yang merujuk pada kode keputusan API. Kode alasan dan tanggal tetap bisa dibuka. Data simulasi ditandai di seluruh aplikasi. Paper Trading memperkirakan nilai bruto sebelum biaya dengan jumlah lot dan harga penutupan fixture.

Navigasi tetap memakai enam rute yang sudah ada. Navigasi bawah memakai label ringkas dan ikon pada ponsel dan tablet, lalu menjadi sidebar mulai 1024 px. Tampilan riset berada di dalam rute Screener. Saat dibuka, fokus berpindah ke judul. Tombol kembali mengembalikan fokus ke kartu asal. Token global memberi warna off-white, permukaan putih, aksen emerald dan warna status dengan label teks, serta radius, jarak, dan fokus keyboard yang konsisten.

## Integritas data

- Nama emiten dibaca dari katalog fixture menggunakan GET saham yang sudah ada. Jika kode tidak tercakup atau katalog gagal dimuat, kartu tetap tersedia dengan keterangan nama belum tersedia.
- Skor keseluruhan memerlukan snapshot `COMPLETE`, tujuan `RESEARCH_ONLY`, penilaian `SCOREABLE`, dan mutu penilaian `AVAILABLE`. Skor faktor juga memerlukan mutu faktor `AVAILABLE`.
- Status ringkas berasal dari kelayakan, bukti faktor yang tersedia, arah data aktual, dan catatan risiko model. Bukti kedaluwarsa atau belum terverifikasi tidak mendapat label arah. Rincian memakai angka dari respons yang sudah didekode Angular tanpa pemformatan yang membulatkan, serta string timestamp UTC asli.
- Kontrak dan rute API tetap sama. Lingkup fase ini adalah Angular dan dokumentasi development.

## Verifikasi pada host development

Gunakan Java 25 dan Node 22.23.2 atau versi yang didukung Angular 22. Dari root repositori:

```bash
CI=true mvn -o -B -f backend/pom.xml clean verify
(cd frontend && npm test && npm run build)
git diff --check
```

Jalankan backend fixture default di satu terminal dengan `java -jar backend/target/phivealpha/phivealpha-backend.jar`. Di terminal lain, jalankan `npm start` dari `frontend/`. Buka `http://127.0.0.1:4282`. Mode default tidak memerlukan kredensial atau PostgreSQL.

1. Periksa Overview, Screener, Weekly Top 5, Stock Analysis, News Intelligence, dan Paper Trading pada 360, 768, dan 1280 px. Periksa navigasi bawah pada dua ukuran pertama, sidebar pada desktop, penanda rute aktif, dan keterbacaan konten terakhir di atas navigasi.
2. Bandingkan urutan kandidat dengan `curl -fsS http://127.0.0.1:8182/api/idx/v1/stock-screener`. Periksa status data, nama emiten, dan bagian saham belum lolos. Gunakan Tab dan Enter untuk membuka riset, buka satu faktor teknis, lalu pilih **Kembali ke kandidat**. Periksa fokus kembali ke tombol semula.
3. Di DevTools, blokir request screener dan buka halaman lagi. Lepas blokir lalu pilih **Coba lagi**. Dengan Local Overrides pada respons GET fixture, coba mutu `STALE`, `UNVERIFIED`, dan `UNAVAILABLE`. Pastikan skor hilang dan status sesuai. Untuk keadaan kosong, gunakan ranking dan keputusan kosong pada respons uji.
4. Dari Weekly Top 5, berita, dan detail riset, buka Stock Analysis. Pastikan query `symbol` memilih kode yang benar. Di Paper Trading, masukkan BBCA dan dua lot. Periksa 200 saham dan nilai bruto yang sesuai respons fixture.

Validasi browser pada host masih diperlukan. Regresi Maven offline berjalan pada salinan sumber backend di direktori sementara karena direktori target repositori read-only di sandbox. Hasilnya 94 lulus dan satu skip connector loopback.

## Phase 1N.1 — Mobile usability polish

Kartu Screener dipadatkan dengan target sentuh 44 px. Skor tampil dengan maksimal dua desimal Indonesia, sementara nilai API tetap tersedia pada rincian dan urutan backend tetap dipakai saat skor sama. Navigasi bawah memakai ikon garis seragam. Unit frontend 16 lulus, build Angular lulus, dan git diff --check lulus. Review Chromium dengan fixture lokal mencakup 360, 768, dan 1280 px. Pemeriksaan meliputi ukuran target, overflow, urutan kandidat, serta Enter dan Spasi untuk membuka riset dan rincian, pengembalian fokus, dan navigasi keyboard.
