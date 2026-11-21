# Backend PhiveAlpha

Spring Boot dan WebFlux menyediakan API fixture di `/api/idx/v1` pada `127.0.0.1:8182`. [Kontrak API](../docs/api/README.md)

```bash
mvn clean verify
java -jar target/phivealpha/phivealpha-backend.jar
```

Default fixture mematikan koneksi PostgreSQL dan inisialisasi SQL. Profil opt-in `phivealpha-persistence` memerlukan koneksi development terisolasi dan penerapan SQL manual. Integration test PostgreSQL memakai `../scripts/validation/phivealpha-persistence-it.sh` dengan cluster sementara. [Runbook persistence](../docs/development/phivealpha-phase-1c.md)

Akses provider HTTP tunduk pada profil opt-in, bukti entitlement Free, batas ledger dan audit. [Guard API.co.id](../docs/development/phivealpha-phase-1h.md)
