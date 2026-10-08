# Van de Booth

Photobooth kiosk desktop: tamu memilih layout strip, berfoto dengan hitung mundur, memilih filter, lalu mencetak, menyimpan, atau mengunduh strip lewat QR. UI React di dalam Electron, logika foto di sidecar Java.

Proyek ini berawal dari Tugas Besar mata kuliah Pemrograman Berorientasi Objek (PBO) semester 3 oleh kelompok SixSeven (repo asli bernama "Photobooth Studio Pro"), lalu dilanjutkan sebagai fondasi produk Van de Booth. GUI Swing lama tersimpan di tag `legacy-swing-ui`.

| Attract | Layout | Capture |
|---|---|---|
| ![Attract](docs/screenshots/1-attract.png) | ![Layout](docs/screenshots/2-layout.png) | ![Capture](docs/screenshots/3-capture.png) |
| **Review** | **Filter** | **Result** |
| ![Review](docs/screenshots/4-review.png) | ![Filter](docs/screenshots/5-filter.png) | ![Result](docs/screenshots/6-result.png) |
| **Pay (demo)** | **Operator: Sharing** | **Operator: Gallery** |
| ![Pay](docs/screenshots/7-pay.png) | ![Sharing](docs/screenshots/9-operator-sharing.png) | ![Gallery](docs/screenshots/10-operator-gallery.png) |

Screenshot dibuat otomatis oleh tes e2e dengan kamera palsu Chromium (area hijau).

## Fitur

- Alur tamu: Attract, Layout, Capture, Review, Filter, Result, dengan idle 90 detik kembali ke awal.
- Layout strip: Vertical 4 foto, Vertical 3 foto, Horizontal 3 foto.
- Hitung mundur 3-2-1 per foto, retake per foto (batas `maxRetakes` per sesi).
- Filter Original, Black & white, Vintage, Warm, dikomposisi di sidecar.
- Footer strip berisi wordmark dan caption acara (`event.name`, `event.date`).
- Cetak strip ke printer (kertas 4x6, single atau two-up) dengan batas salinan per sesi.
- Unduh lewat QR di jaringan lokal; simpan lokal ke `~/VanDeBooth/exports`.
- Setiap sesi diarsipkan di `~/VanDeBooth/sessions/<timestamp>/`.
- Pembayaran demo opsional (tanpa gateway, tanpa transaksi nyata).
- Mode Operator untuk mengatur acara, foto, pembayaran, berbagi, cetak, galeri, dan status.
- Foto hanya ada di komputer booth; tidak ada unggahan ke internet.

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
| `npm run e2e` | Playwright: alur penuh (pembayaran ON/OFF, Mode Operator) dengan kamera palsu, menyimpan screenshot ke `docs/screenshots/` |

Mode kiosk layar penuh: `npm run dev -- --kiosk`. Sidecar bisa dijalankan sendiri: `VANDEBOOTH_TOKEN=<min 16 karakter> java -jar target/vandebooth.jar --server`.

### Konfigurasi

Sebagian besar pengaturan diubah lewat Mode Operator. Nilai awal ada di `src/main/resources/config.properties` dan bisa ditimpa oleh `config.properties` di direktori kerja atau `-Dvandebooth.<kunci>=...`:

```properties
output.dir=~/VanDeBooth
event.name=Sample event
event.date=2026-10-12
maxRetakes=2
payment.enabled=false
share.port=8080
print.mode=system      # file = tulis PNG siap cetak ke <output>/print-queue/ (tanpa printer)
```

Perubahan dari Mode Operator disimpan di folder config pengguna: `%APPDATA%\VanDeBooth` (Windows) atau `~/.config/vandebooth`.

## Mode Operator

1. Di layar sambut, tekan dan tahan wordmark "Van de Booth" selama 3 detik.
2. Pertama kali: buat PIN 4 sampai 8 digit (diketik dua kali). Tidak ada PIN bawaan; PIN disimpan sebagai hash PBKDF2 dengan salt di folder config pengguna.
3. Berikutnya: masukkan PIN. Lima kali salah berturut-turut mengunci login selama 30 detik.

| Menu | Isi |
|---|---|
| Event | Nama dan tanggal acara (chip layar sambut dan footer strip) |
| Photos | Batas retake, detik hitung mundur (2-5), jeda antar foto |
| Payment | Pembayaran demo on/off dan harga |
| Sharing | Berbagi on/off, alamat jaringan, masa berlaku link (1, 6, 24 jam), QR uji |
| Printing | Pilih printer, salinan per tamu, single atau two-up, cetak halaman uji |
| Gallery | Thumbnail sesi, hapus sesi, export semua ke ZIP, hapus sesi lama |
| Status | Kamera, printer, versi, ruang disk, alamat berbagi, salin diagnostik |

Perubahan berlaku tanpa restart. Sesi operator berakhir setelah 5 menit tanpa aktivitas.

Lupa PIN: tutup aplikasi, hapus `operator-pin.properties` di folder config pengguna, lalu buat PIN baru.

## Berbagi lewat jaringan lokal

Layar hasil menampilkan QR "Scan to download". Tamu memindai QR untuk membuka halaman unduh strip dan setiap foto langsung dari komputer booth.

- Ponsel tamu harus terhubung ke Wi-Fi atau hotspot yang sama dengan komputer booth.
- Server unduh berjalan di port `share.port` (bawaan 8080), hanya melayani `/s/<token>`. API utama tetap hanya di 127.0.0.1 dengan token.
- Alamat IPv4 privat dideteksi otomatis. Bila QR tidak terbuka, isi alamat yang benar di Mode Operator > Sharing.
- Saat pertama berjalan, Windows meminta izin firewall: izinkan untuk jaringan privat.
- Link berlaku 6 jam (bisa 1 atau 24 jam) lalu mengembalikan 404; foto tetap tersimpan di komputer.
- Uji sebelum acara lewat QR di Mode Operator > Sharing.

## Arsitektur

```
Electron (desktop/) --spawn--> sidecar Java (target/vandebooth.jar --server)
      | memuat                    ^ HTTP 127.0.0.1:<port acak>, header X-Booth-Token
      v                           |
React + Vite + TS (ui/) ----------+ /api/*        kamera: getUserMedia
```

- `ui/`: state machine berbasis reducer, design system di `ui/src/styles/tokens.css`, semua teks di `ui/src/strings.ts`.
- `desktop/`: menjalankan sidecar dengan port dan token acak, mematikannya saat aplikasi keluar.
- `src/main/java`: `server` (SidecarServer, SidecarApp), `service` (Facade `PhotoboothService`), `share` (ShareServer), `print`, `payment`, `admin`, `template`, `filter`, `export`, `repository`, `config`, `hardware`.
- Spesifikasi lengkap: `docs/design-system.md`, `docs/flow.md`, `docs/architecture.md`, `docs/phase3.md`.

Design pattern: 3 pola GoF (Singleton `CameraManager`, Strategy `ExportStrategy` (Local, Print)/`FilterStrategy`/`PaymentProvider`, Facade `PhotoboothService`) ditambah Simple Factory `TemplateFactory`.

## Tim pengembang awal

| Nama | Kontribusi utama |
|---|---|
| Umem (Hisyam Khaeru Umam) | Integrasi dan merge antar-branch, GUI, wiring `MainApp` |
| Ibnu (Ibun) | `CameraManager`, `PhotoboothService` awal, fitur video |
| Ihsan Ramadhan (Ican) | Template, `TemplateFactory`, filter, unit test |

## Lisensi

MIT, lihat `LICENSE`. Status lisensi aset non-kode (font OFL, audio) dicatat di `ASSETS.md`.
