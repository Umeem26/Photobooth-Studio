# Van de Booth

Photobooth kiosk desktop: tamu memilih layout strip, berfoto dengan hitung mundur, memilih filter, lalu menyimpan strip. UI React di dalam Electron, logika foto di sidecar Java.

Proyek ini berawal dari Tugas Besar mata kuliah Pemrograman Berorientasi Objek (PBO) semester 3 oleh kelompok SixSeven (repo asli bernama "Photobooth Studio Pro"), lalu dilanjutkan sebagai fondasi produk Van de Booth. GUI Swing lama tersimpan di tag `legacy-swing-ui`.

| Attract | Layout | Capture |
|---|---|---|
| ![Attract](docs/screenshots/1-attract.png) | ![Layout](docs/screenshots/2-layout.png) | ![Capture](docs/screenshots/3-capture.png) |
| **Review** | **Filter** | **Result** |
| ![Review](docs/screenshots/4-review.png) | ![Filter](docs/screenshots/5-filter.png) | ![Result](docs/screenshots/6-result.png) |

Screenshot dibuat otomatis oleh tes e2e dengan kamera palsu Chromium (area hijau).

## Fitur

- Alur tamu: Attract, Layout, Capture, Review, Filter, Result, dengan idle 90 detik kembali ke awal.
- Layout strip: Vertical 4 foto, Vertical 3 foto, Horizontal 3 foto.
- Hitung mundur 3-2-1 per foto, retake per foto (batas `maxRetakes` per sesi).
- Filter Original, Black & white, Vintage, Warm, dikomposisi di sidecar.
- Footer strip berisi wordmark dan caption acara (`event.name`, `event.date`).
- Simpan lokal ke `~/VanDeBooth/exports`; setiap sesi diarsipkan di `~/VanDeBooth/sessions/<timestamp>/`.
- Foto hanya disimpan di disk lokal, tidak diunggah ke mana pun.

## Prasyarat

JDK 17 atau lebih baru dan Node.js 20 atau lebih baru. Maven tidak perlu dipasang (sudah ada wrapper).

## Menjalankan

```bash
git clone https://github.com/Umeem26/Photobooth-Studio.git
cd Photobooth-Studio
npm install
npm run dev       # Vite + Electron; Electron menjalankan sidecar Java
```

| Perintah | Isi |
|---|---|
| `npm run build` | Membuat `target/vandebooth.jar` dan `ui/dist/` |
| `npm test` | Vitest (UI) dan tes Node untuk shell Electron |
| `./mvnw verify` | Tes JUnit backend (headless) dan fat JAR (Windows: `mvnw.cmd verify`) |
| `npm run e2e` | Playwright: alur penuh dengan kamera palsu, menyimpan screenshot ke `docs/screenshots/` |

Mode kiosk layar penuh: `npm run dev -- --kiosk`. Sidecar bisa dijalankan sendiri: `VANDEBOOTH_TOKEN=<min 16 karakter> java -jar target/vandebooth.jar --server`.

### Konfigurasi

`config.properties` di direktori kerja (atau `-Dvandebooth.<kunci>=...`):

```properties
output.dir=~/VanDeBooth
event.name=Sample event
event.date=2026-10-12
maxRetakes=2
```

## Arsitektur

```
Electron (desktop/) --spawn--> sidecar Java (target/vandebooth.jar --server)
      | memuat                    ^ HTTP 127.0.0.1:<port acak>, header X-Booth-Token
      v                           |
React + Vite + TS (ui/) ----------+ /api/*        kamera: getUserMedia
```

- `ui/`: state machine berbasis reducer, design system di `ui/src/styles/tokens.css`, semua teks di `ui/src/strings.ts`.
- `desktop/`: menjalankan sidecar dengan port dan token acak, mematikannya saat aplikasi keluar.
- `src/main/java`: `server` (SidecarServer), `service` (Facade `PhotoboothService`), `template`, `filter`, `export`, `repository`, `config`, `hardware`.
- Spesifikasi lengkap: `docs/design-system.md`, `docs/flow.md`, `docs/architecture.md`.

Design pattern: 3 pola GoF (Singleton `CameraManager`, Strategy `ExportStrategy`/`FilterStrategy`, Facade `PhotoboothService`) ditambah Simple Factory `TemplateFactory`.

## Tim pengembang awal

| Nama | Kontribusi utama |
|---|---|
| Umem (Hisyam Khaeru Umam) | Integrasi dan merge antar-branch, GUI, wiring `MainApp` |
| Ibnu (Ibun) | `CameraManager`, `PhotoboothService` awal, fitur video |
| Ihsan Ramadhan (Ican) | Template, `TemplateFactory`, filter, unit test |

## Lisensi

MIT, lihat `LICENSE`. Status lisensi aset non-kode (font OFL, audio) dicatat di `ASSETS.md`.
