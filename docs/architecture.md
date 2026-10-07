# Van de Booth: Arsitektur (Fase 2)

## Komponen
```
Electron (desktop/)  --spawn-->  Java sidecar (target/vandebooth.jar --server)
   |  loads                           ^  HTTP 127.0.0.1:<port acak>, header X-Booth-Token
   v                                  |
React + Vite + TS (ui/)  -----------> /api/*
   kamera: getUserMedia (Chromium)
```
- **UI** (`ui/`): React 18 + Vite + TypeScript, state machine berbasis reducer, CSS biasa + `tokens.css` (tanpa Tailwind). Fon dari `@fontsource-variable/*` (offline). Satu `strings.ts`.
- **Desktop** (`desktop/`): shell Electron minimal: menjalankan sidecar di port acak dengan token acak (env), menunggu `/health`, memuat UI (dev: server Vite; prod: file hasil build), fullscreen kiosk opsional lewat flag, mematikan sidecar saat keluar. Izin kamera diberikan otomatis untuk origin aplikasi.
- **Sidecar** (Java, kode backend yang ada): mode `--server` di `MainApp`, memakai `com.sun.net.httpserver.HttpServer` bawaan JDK, hanya bind 127.0.0.1, menolak request tanpa token. JSON dengan Gson (Maven Central). Memakai ulang `PhotoboothService` (Facade), Template*, FilterStrategy, ExportStrategy, SessionRepository, config.properties.
- GUI Swing lama tetap ada sampai alur baru lulus; di tugas terakhir fase ini buat tag git `legacy-swing-ui`, lalu hapus kelas Swing yang tidak lagi dipakai (jangan hapus logika backend).

## API sidecar (semua JSON kecuali gambar)
| Method | Path | Fungsi |
|---|---|---|
| GET | `/health` | `{ok:true, version}` |
| GET | `/api/config` | event.name, event.date, maxRetakes, photosPerLayout (tanpa path sensitif) |
| GET | `/api/layouts` | `[{id,name,description,photos,orientation}]` untuk `vertical-4`, `vertical-3`, `horizontal-3` |
| GET | `/api/filters` | `[{id,name}]`: `original`, `mono`, `vintage`, `warm` |
| POST | `/api/sessions` | body `{layout}` -> `{sessionId}` |
| PUT | `/api/sessions/{id}/frames/{index}` | body JPEG mentah; menyimpan/mengganti frame (dipakai juga untuk retake) |
| POST | `/api/sessions/{id}/compose` | `{filter}` -> `{stripUrl}`; gambar via `GET /api/sessions/{id}/strip.png` |
| GET | `/api/sessions/{id}/frames/{index}` | JPEG tersimpan (untuk Review) |
| POST | `/api/sessions/{id}/export` | `{strategy:"local"}` -> `{path}` (menyalin strip ke `<output>/exports`) |
| POST | `/api/sessions/{id}/abandon` | menandai sesi dibatalkan |
Kode error: 400 input salah, 401 token salah, 404 sesi/frame tidak ada, 409 frame belum lengkap saat compose, 500 error tak terduga. Body error `{error, message}`.

## Backend yang perlu ditambah di Fase 2
`WarmFilterStrategy` (+ test); komposisi strip menerima frame resolusi apa pun dan men-scale ke 4:3; footer strip: wordmark "Van de Booth" ("de" italic, "oo" #D9411E) + caption `event.name · event.date`, memakai TTF Fraunces dari resources (unduh dari repo resmi google/fonts, lisensi OFL, simpan lisensinya di ASSETS.md; jika gagal pakai font serif logis dan laporkan); `SidecarServer`; konfigurasi `event.name`, `event.date`, `maxRetakes`.

## Struktur repo
`src/` (Java, Maven) · `ui/` · `desktop/` · `docs/` · `CLAUDE.md`. Skrip root `package.json`: `dev` (Vite + Electron + sidecar), `build`, `test`, `e2e`.

## Anggaran performa
Preview >= 30 fps di 1080p. Jepret -> thumbnail < 500 ms. Compose 4 foto 1080p < 1,5 detik. Start sidecar -> `/health` < 3 detik.

## Strategi tes (otomatis, tanpa webcam dan tanpa tes manual)
- Java: JUnit headless untuk setiap endpoint (HttpServer di port acak, token), compose, filter, warm.
- UI: Vitest untuk reducer dan strings; Playwright (Chromium) dengan kamera palsu (`--use-fake-device-for-media-stream --use-fake-ui-for-media-stream`) menjalankan alur penuh Attract -> Result terhadap sidecar sungguhan dan menyimpan screenshot 1920x1080 tiap layar ke `docs/screenshots/` (juga bahan media LinkedIn).
- Bandingkan tiap screenshot dengan mockup yang sesuai (baca keduanya) dan laporkan selisih visual yang signifikan.
