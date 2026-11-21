# Arsitektur PhiveAlpha

Angular meminta snapshot IDX melalui Spring WebFlux. Provider fixture memasok data untuk Overview, Weekly Top 5, Stock Analysis, News Intelligence dan estimasi Paper Trading. Seluruh rute aplikasi memakai HTTP GET.

Engine riset memakai kalender sesi dan data as-of untuk pemeringkatan likuiditas, scoring berita dan replay sintetis. PostgreSQL R2DBC tersedia melalui profil development opt-in, dengan migrasi manual berskema `phivealpha_idx`. Akses data HTTP opt-in memakai entitlement per produk, ledger quota dan audit.

[Status implementasi](../development/current-state.md)
