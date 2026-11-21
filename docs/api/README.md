# API PhiveAlpha

Backend fixture lokal tersedia di `http://127.0.0.1:8182`. Prefiks seluruh endpoint IDX adalah `/api/idx/v1`.

| GET | Hasil |
| --- | --- |
| `/stocks` | Snapshot saham dari universe fixture |
| `/stocks/{symbol}` | Detail saham, dengan 404 untuk kode di luar fixture |
| `/weekly-top-five` | Maksimal lima saham menurut perubahan harga mingguan |
| `/headlines` | Headline sintetis |
| `/paper-trade/preview?symbol=BBCA&lots=2` | Estimasi bruto sebelum biaya berdasarkan 100 saham per lot |

[Kontrak dan provenance fixture](../development/phivealpha-phase-1a.md)
