# PhiveAlpha Phase 1M — Premium UI Modernization

Dikerjakan di atas worktree Phase 1L pada `main` commit `c614812`. Fase ini memakai desain light-only. Warna dasar berasal dari token CSS bersama untuk background `#F7F9F7`, kartu putih `#FFFFFF`, permukaan sekunder `#E8F0EB`, aksen emerald `#087F5B`, dan teks `#172B22`.

- Shell memakai navigasi grid di ponsel dan tablet, lalu beralih ke sidebar sticky pada 1024 px. Semua rute mempertahankan penanda halaman aktif dan memiliki fokus keyboard. Tautan lewati navigasi menuju konten utama.
- Overview memakai kartu harga penutupan dan sektor fixture. Weekly Top 5 menunjukkan urutan dan perubahan mingguan. Link ticker tetap membuka Stock Analysis. Ketiga halaman lainnya memakai pola kartu, form, metrik, dan status yang sama.
- Stock Screener memisahkan shortlist maksimum lima dari satu panel detail riset yang bisa ditutup. Kandidat pertama dipilih saat snapshot tersedia. Pemilihan kandidat memperbarui dan membuka kembali detail. Provenance dan keputusan yang belum lolos tetap tersedia.
- Phase 1L tetap memakai ranking API, pemeriksaan kualitas sebelum menampilkan skor, label fixture dan development persistence, timestamp UTC, alasan penolakan, serta loading, empty state, error, dan retry. Seluruh operasi Paper Trading tetap berupa estimasi fixture.

## Hasil regresi

- `npm test` lulus dengan 13 tes, termasuk pilihan kandidat, buka kembali detail, provenance, skor tersembunyi, retry, dan navigasi.
- `npm run build` lulus untuk Angular PhiveAlpha. Kelima kandidat dari JSON fixture WebFlux juga dapat dipilih di renderer Angular in-process dengan locale `id-ID`.
- `CI=true mvn -o -B -f backend/pom.xml clean verify` lulus dengan 94 tes lulus, satu skip connector loopback, dan nol gagal.
- `git diff --check` dan pemeriksaan whitespace file baru lulus. Aturan CSS diperiksa pada 360, 768, dan 1280 px. Pemeriksaan visual browser masih perlu dijalankan di host development karena Chromium dan Firefox headless gagal startup di sandbox.

## Pemeriksaan host development

Ikuti perintah menjalankan backend fixture dan Angular di [handoff Phase 1L](phivealpha-phase-1l.md). Buka `http://127.0.0.1:4282` dan periksa keenam rute dengan DevTools.

| Lebar viewport | Navigasi | Stock Screener |
| --- | --- | --- |
| 360 px | Enam rute dalam dua baris | Shortlist di atas detail |
| 768 px | Enam rute dalam satu baris | Shortlist dan detail berdampingan |
| 1280 px | Sidebar vertikal | Shortlist dan detail berdampingan |

Periksa halaman tanpa scroll horizontal, indikator fokus keyboard terlihat, detail bisa ditutup dan dibuka dengan keyboard, serta status kualitas dan provenance tetap terbaca. Uji kegagalan request screener dan pemulihan lewat **Coba lagi** sesuai runbook fixture.

Perubahan fase ini terbatas pada frontend Angular PhiveAlpha, unit test, dan dokumentasi development. Worktree Phase 1L serta file lokal lain dipertahankan.
