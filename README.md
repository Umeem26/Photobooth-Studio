# Van de Booth

Aplikasi photobooth desktop berbasis Java Swing: ambil beberapa foto dari webcam, gabungkan menjadi strip, lalu simpan ke komputer.

Proyek ini berawal dari Tugas Besar mata kuliah Pemrograman Berorientasi Objek (PBO) semester 3 oleh kelompok SixSeven (repo asli bernama "Photobooth Studio Pro"), lalu dilanjutkan sebagai fondasi produk Van de Booth.

## Fitur

- Pilih layout: Strip Vertikal atau Strip Horizontal, masing-masing 2, 3, atau 4 foto, dengan pratinjau layout.
- Live preview webcam dengan hitung mundur 3 detik di layar, pilihan kamera, dan ambil ulang foto terakhir.
- Filter Normal, Grayscale, dan Vintage, diterapkan pada preview dan hasil foto.
- Rekaman video selama hitung mundur, digabung menjadi video strip MP4 sesuai layout, dengan pratinjau.
- Simpan strip PNG ke lokasi pilihan pengguna.
- Arsip sesi otomatis di `~/VanDeBooth/sessions/<timestamp>/` (foto per slot, `strip.png`, `meta.properties`).
- Penyusunan strip dan penyimpanan berjalan di background thread, GUI tidak membeku.
- Tema gelap FlatLaf.

Util pembuat QR Code (`utils.QrCodeGenerator`) tersedia dan teruji, tetapi belum terhubung ke GUI.

![Pemilihan template](screenshots/template_screen.png)
![Kamera dan filter](screenshots/camera_screen.png)

## Menjalankan

Prasyarat: JDK 17 atau lebih baru dan webcam. Maven tidak perlu dipasang (sudah ada wrapper).

```bash
git clone https://github.com/Umeem26/Photobooth-Studio.git
cd Photobooth-Studio
./mvnw verify                 # Windows: mvnw.cmd verify
java -jar target/vandebooth.jar
```

`verify` menjalankan seluruh unit test (headless, tanpa webcam) dan membuat fat JAR `target/vandebooth.jar`.

### Konfigurasi

Folder output default `~/VanDeBooth` (video di `videos/`, arsip sesi di `sessions/`). Ubah dengan salah satu cara:

- file `config.properties` di direktori kerja berisi `output.dir=D:/Booth`
- `java -Dvandebooth.output.dir=D:/Booth -jar target/vandebooth.jar`

## Arsitektur

Paket di `src/main/java`: `gui` (Swing), `service` (Facade), `hardware` (kamera), `factory`, `template`, `filter`, `export`, `repository`, `config`, `utils`, `exception`.

Design pattern yang dipakai: 3 pola GoF ditambah Simple Factory.

| Pola | Kelas |
|---|---|
| Singleton (thread-safe, lazy) | `hardware.CameraManager` |
| Strategy | `export.ExportStrategy`, `filter.FilterStrategy` |
| Facade | `service.PhotoboothService` |
| Simple Factory (bukan GoF) | `factory.TemplateFactory` |

## Tim pengembang awal

| Nama | Kontribusi utama |
|---|---|
| Umem (Hisyam Khaeru Umam) | Integrasi dan merge antar-branch, GUI, wiring `MainApp` |
| Ibnu (Ibun) | `CameraManager`, `PhotoboothService` awal, fitur video |
| Ihsan Ramadhan (Ican) | Template, `TemplateFactory`, filter, unit test |

## Lisensi

MIT, lihat `LICENSE`. Status lisensi aset non-kode dicatat di `ASSETS.md`.
