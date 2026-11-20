# PhiveAlpha Phase 1F — News-Driven Candidate Scoring

`NewsDrivenScoringService` menilai kandidat riset swing IDX dengan horizon studi 2–10 sesi perdagangan. Versi model fixture adalah `fixture-news-v1`. Bobot, koefisien, dan horizon adalah hipotesis. Belum ada backtest, kalibrasi probabilitas, atau pengukuran akurasi prediksi.

## Arsitektur dan kontrak

`score(universe, inputBySymbol, market, cutoff)` menerima evaluasi dari `LiquidUniverseService`, snapshot berita, momentum dan flow per simbol IDX kanonik, serta snapshot regime pasar bersama. Inputs adalah fixture yang disiapkan caller. Semua pekerjaan sinkron dan in-memory. Service tidak menarik data, memanggil NLP, menyimpan state, menerbitkan event, atau mengubah endpoint fixture.

- Universe dan setiap kandidat harus dievaluasi pada cutoff yang sama. Referensi master dan kalender harus tersedia pada waktu itu. Hanya baris `ELIGIBLE` dengan kualitas `COMPLETE`, candle lengkap, dan versi yang cocok yang dinilai. Momentum tinggi tidak melewati gate universe. Report mengembalikan `UNIVERSE_UNVERIFIED` jika referensi global gagal.
- Tiap snapshot membawa sumber, versi, waktu data `asOf`, waktu data beserta revisi atau anotasinya tersedia `availableAt`, dan kualitas deklaratif. Waktu wajib memenuhi `asOf <= availableAt <= cutoff`. Snapshot masa depan diberi `UNAVAILABLE` pada cutoff dan provenance masa depan tidak ditampilkan. Snapshot yang tidak dapat dibuktikan memakai `UNVERIFIED`.
- Kualitas faktor `AVAILABLE` berarti melewati pemeriksaan fixture. `UNAVAILABLE` berarti input tidak ada atau belum tersedia. `STALE` berarti data melewati batas usia atau dinyatakan stale oleh sumber. `INCOMPLETE` berarti ada nilai hilang atau tidak valid. `UNVERIFIED` berarti provenance/basis belum dapat dibuktikan atau anotasi berita bertentangan. Status eksplisit dari sumber dipertahankan. Default usia maksimum snapshot adalah tujuh hari kalender sejak `asOf`.
- Semua faktor wajib lolos. Faktor gagal memiliki skor dan kontribusi null, alasan, serta provenance terdokumentasi bila tersedia. Kandidat dengan faktor gagal mendapatkan `INSUFFICIENT_DATA` atau `UNVERIFIED`, tanpa skor keseluruhan atau rank. Kualitas agregat mengambil prioritas `UNVERIFIED`, `INCOMPLETE`, `STALE`, `UNAVAILABLE`, lalu `AVAILABLE`. Breakdown mempertahankan kualitas masing-masing faktor.
- `assessment` memuat simbol, skor, faktor (raw metrics, sumber, versi, waktu, kualitas, penjelasan dan kontribusi), katalis, risk notes, cutoff, source versions, confidence dan reasons. `COMPLETE_FIXTURE` menjelaskan kelengkapan input yang digunakan. Rank diberikan hanya pada maksimal lima kandidat dari status `SCOREABLE` melalui `ranking`. Tie-breaker adalah simbol naik setelah skor keseluruhan turun. `assessments` menyimpan audit kandidat yang dinilai, termasuk kandidat yang gugur dan kandidat di luar batas lima.

## Rumus

Skor faktor di-clamp ke 0–100 dan dibulatkan ke enam desimal. Kontribusi adalah skor faktor × bobot / 100. Skor keseluruhan menjumlahkan kontribusi dan membulatkan hasil ke enam desimal untuk ranking. `pct(A,B) = 100 × (A − B) / B`.

| Faktor | Bobot default | Raw metrics dan skor sebelum clamp |
| --- | ---: | --- |
| News catalyst | 30% | Dampak event = sentimen × materialitas × relevansi × kebaruan × (1 − antisipasi). Sentimen dalam [-1,1], empat rating lain dalam [0,1]. Pilih dampak absolut terbesar dan gunakan ID event naik untuk seri. Skor = 50 + 50 × dampak bertanda. Coverage tersedia tanpa event efektif menghasilkan 50 secara eksplisit. Dampak ≤ -0,25 menghasilkan catatan risiko, termasuk bila ada katalis lain yang lebih kuat. |
| Price momentum | 25% | Tiga close positif pada basis harga yang konsisten. Skor = 50 + 5 × `pct(latest, close5)` + 2 × `pct(latest, close10)`. Horizon 5/10 adalah jumlah sesi BEI. |
| Volume/liquidity | 20% | Turnover median, turnover/volume terakhir, hari aktif, hari likuid dan sesi yang diharapkan dari Phase 1E. Skor = 40 × min(1, median / (2 × minimum median Phase 1E)) + 30 × hari aktif / sesi + 30 × hari likuid / sesi. Metadata harga memakai sumber candle, waktu penutupan sesi terakhir dan ketersediaan candle paling akhir. Versi derivasi menggabungkan versi universe dan kalender. |
| Foreign/broker flow | 15% | Net beli asing dan net beli kohor broker yang ditentukan, keduanya dibagi turnover yang sepadan. Skor = 50 + 25 × clamp(10 × rasio asing, -1, 1) + 25 × clamp(10 × rasio broker, -1, 1). Kedua seri wajib ada. Net memang nol bila data sumber melaporkan nol. |
| Market regime/risk | 10% | Return benchmark lima sesi dalam persen dan risk level fixture [0,1]. Skor = 50 + 10 × return benchmark − 30 × risk level. Return negatif memberi `BEARISH_MARKET`. Risk level ≥ 0,70 memberi `ELEVATED_MARKET_RISK`. |

Atur `news-weight`, `momentum-weight`, `liquidity-weight`, `flow-weight`, `regime-weight`, `max-snapshot-age` dan `news-lookback` melalui prefix `phivealpha.news-scoring`. Kelima bobot harus positif dan totalnya tepat 100. Kedua durasi harus positif. Lookback berita default tujuh hari kalender dari cutoff ke publikasi event kanonik.

### Berita yang tersedia pada cutoff

Event memiliki ID kanonik dalam cakupan emiten, label, serta `publishedAt <= firstSeenAt <= availableAt`. Waktu terakhir mencakup kapan anotasi fixture tersedia. Ketiga waktu harus tidak lewat cutoff. Snapshot berita harus mencakup event tersebut menurut `asOf`. Event yang baru tersedia setelah cutoff disaring sebelum validasi isi, deduplikasi, dan agregasi. Report lama tetap sama ketika fixture mendapat event yang datang belakangan.

Sindikasi dengan ID yang sama hanya dihitung sekali. Versi dengan waktu ketersediaan paling awal menjadi representasi kanonik. Konflik rating untuk ID yang sama membuat faktor unverified. Deduplikasi dilakukan sebelum lookback sehingga repost yang diketahui berasal dari event lama tidak memperbarui usianya. Breakdown mencatat jumlah event unik, event tanpa materialitas, event yang sudah sepenuhnya diantisipasi, dan jumlah event dengan dampak negatif material. Positif tanpa materialitas atau kejutan tidak otomatis mendukung skor.

## Contoh hasil fixture

Proyeksi ringkas dari fixture pada `2026-11-13T05:00:00Z`. Scope riset memakai contoh sintetis `GOOD`, `NEG`, dan `THIN`.

| Rank | Simbol | Overall | News | Momentum | Liquidity | Flow | Regime | Catalyst / risk |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | GOOD | 61,500000 | 50 | 64 | 90 | 50 | 50 | Penghargaan nonmaterial dan konfirmasi yang sudah diantisipasi. Tanpa katalis efektif. |
| 2 | NEG | 49,500000 | 10 | 64 | 90 | 50 | 50 | Penghentian produksi. `MATERIAL_NEGATIVE_CATALYST`. |

Keduanya mengembalikan confidence `COMPLETE_FIXTURE` dan source versions seperti `synthetic-universe-v1`, `liquid-universe-calendar-v1`, dan `fixture-v1`. `THIN` gagal gate likuiditas meskipun snapshot momentum positif tersedia. Pada fixture flow hilang, assessment berstatus `INSUFFICIENT_DATA` dengan skor keseluruhan null.

## Batasan

Caller bertanggung jawab memasok data point-in-time yang benar. Input fixture close 5/10 adalah snapshot yang sudah disiapkan, termasuk penentuan jarak sesi BEI, basis harga, dan versi koreksi. Service memeriksa ketersediaan snapshot dan angka, sementara candle penyusun dan atribusi historis belum dihitung di Phase 1F. Versi derivasi liquidity membawa versi universe dan kalender. Versi vendor per candle belum tersedia pada model Phase 1E. Feed berita perlu bukti cakupan, pemetaan emiten, first-seen dan deduplikasi sebelum dipakai pada riset historis. Flow membutuhkan kohor broker yang ditetapkan sebelum cutoff dan denominator untuk cakupan yang sama. Risk level fixture adalah anotasi tanpa model estimasi risiko.

Ranking ini hanya untuk riset. Return realisasi, biaya, slippage, dan ukuran posisi berada di luar kontrak. Bearish regime dapat menurunkan skor tanpa mengeluarkan seluruh kandidat. Sinyal, setup dan order execution tetap di luar service.

## File

- `screening/news/NewsDrivenScoringService.java`, `NewsScoring.java`, dan `NewsScoringPolicy.java` menambah engine, kontrak dan konfigurasi.
- `PhiveAlphaApplication.java` mengikat policy Spring. `LiquidUniverse.java` dan `LiquidUniverseService.java` membawa provenance waktu dan sumber candle untuk faktor likuiditas.
- `NewsDrivenScoringServiceTest.java`, fixture `phivealpha/news-scoring/events.json`, dan `IdxFoundationTest.java` mencakup skenario faktor, ranking dan isolasi runtime fixture.

## Validasi offline

- Maven `clean verify` mencatat 265 kasus, 264 lulus, tanpa kegagalan atau error. Satu uji socket loopback Phase 1D.2 dilewati karena sandbox. Tujuh tes scoring baru mencakup fixture positif nonmaterial, negatif material, sudah diantisipasi, sindikasi dan repost, data masa depan, momentum saham tidak likuid, flow hilang dan parsial, bearish regime, seri, batas lima, input stale/unverified, dan kontrak output tanpa sinyal.
- Binding property Spring dan regresi endpoint fixture lulus. Kedua paket backend serta kedua build produksi Angular lulus. Unit frontend lulus untuk market freshness (8), label AI (10), sumber berita (41), tutorial (41), serta regresi alur request berita. CoinMind masih memunculkan peringatan template lama.
- Tidak ada request ke API.co.id, Gemini, atau provider eksternal, dan tidak ada perubahan database atau produksi, commit, maupun push.

```bash
JDK_JAVA_OPTIONS="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar" \
  mvn -o -f backend/pom.xml clean verify
mvn -o -f backend/pom.xml -Pphivealpha -DskipTests package
(cd frontend && node test-market-data.mjs && npm run test:ai-status && npm run test:news && \
  npm run test:tutorial && npm run build && npm run build:phivealpha)
```
