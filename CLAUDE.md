# Van de Booth (id: vandebooth)

Aplikasi photobooth desktop Java Swing. Fondasi: repo tugas besar PBO "Photobooth-Studio".

## Aturan tetap (berlaku di semua fase)

- Commit langsung ke `main`, commit kecil dan fokus satu perubahan.
- Dilarang: force-push, rewrite riwayat (rebase/amend/filter-repo pada commit yang sudah ada), membuat GitHub Release.
- Jangan pernah mencetak nilai secret (token, client secret, password) ke chat, log, atau file.
- Dependency hanya dari Maven Central, dikelola lewat `pom.xml`. Jangan commit JAR.
- Hemat token: jangan membaca ulang seluruh repo, baca hanya file/bagian yang relevan; jangan menjelaskan panjang.
- Laporan akhir tiap tugas maksimal 15 baris.

## Build

- `./mvnw -q verify` (Windows: `mvnw.cmd -q verify`) menjalankan test dan membuat `target/vandebooth.jar`.
- Jalankan aplikasi: `npm run dev`. Sidecar saja: `VANDEBOOTH_TOKEN=... java -jar target/vandebooth.jar --server`. Java 17.
- Test harus headless (tanpa webcam/GUI).

## Struktur

- `src/main/java`: `MainApp` (default package, mode `--server`), `server` (sidecar HTTP, SidecarApp), `service` (Facade), `share` (ShareServer jaringan lokal), `print`, `payment`, `admin` (Mode Operator), `hardware` (kamera), `factory`, `template`, `filter`, `export`, `repository`, `config`, `utils`, `exception`. GUI Swing lama ada di tag `legacy-swing-ui`.
- `ui/` (React + Vite + TS), `desktop/` (Electron), `e2e/` (Playwright), `docs/` (spesifikasi, mockup, screenshot). Skrip root: `npm run dev|build|test|e2e`.
- Konfigurasi: `src/main/resources/config.properties` (default output `~/VanDeBooth`), ditimpa `./config.properties`, `<folder config pengguna>/config.properties` (Mode Operator), lalu `-Dvandebooth.<kunci>=...`. Tes wajib memakai `vandebooth.config.dir` sementara; jangan menulis PIN/secret ke repo.

## Aturan UI (Fase 2+)

- Sebelum mengerjakan UI baca docs/ (design-system.md, flow.md, ARCHITECTURE.md, phase2-architecture.md, mockups/).
- UI berbahasa Inggris lewat strings.ts.
- Warna hanya dari token; tanpa gradasi dan emoji.
- Tombol >= 96 px pada kanvas 1920x1080.
