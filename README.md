# PhiveAlpha

Advisor riset swing trading saham IDX. Mode fixture menyediakan snapshot IDX, Weekly Top 5, analisis saham, berita sintetis dan estimasi nilai lot untuk simulasi.

## Development lokal

Gunakan Java 25, Maven dan Node yang didukung Angular 22 (misalnya 22.23.2 atau 24.15+ pada seri 24).

```bash
mvn -f backend/pom.xml clean verify
java -jar backend/target/phivealpha/phivealpha-backend.jar
```

Di terminal lain:

```bash
cd frontend
npm ci
npm test
npm run build
npm start
```

Buka `http://127.0.0.1:4282`. Dev server meneruskan `/api/idx/v1/**` ke backend di `127.0.0.1:8182`. Build Angular ada di `frontend/dist/phivealpha/browser`. Mode fixture berjalan tanpa kredensial atau database.

- [Backend dan profil development](backend/README.md)
- [Frontend](frontend/README.md)
- [Status dan riwayat migrasi](docs/development/current-state.md)
