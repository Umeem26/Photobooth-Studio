# Van de Booth: Fase 4 (Installer, README final, CI, Release draft)

Melengkapi `phase3.md`. Aset baru di paket ini: `docs/brand/icon.ico` + `icon-1024.png` + `icon-512.png` (ikon aplikasi: cincin lensa di kotak vermilion, sama dengan mockup 5) dan `docs/assets/test-feed.mjpeg` (video uji sintetis 1280x720, 12 fps, 4 detik, berulang; ilustrasi dua orang, **bukan orang sungguhan**).

## 1. Paket Windows (x64)
- electron-builder, target **NSIS** (bukan one-click: pemasangan per-pengguna, pilihan folder, shortcut desktop + Start Menu). `appId=com.umem.vandebooth`, `productName="Van de Booth"`, ikon dari `desktop/build/icon.ico` (salin dari docs/brand). Satu instance saja (`requestSingleInstanceLock`).
- **Java runtime dibundel**, pengguna tidak perlu memasang Java: buat runtime ringkas dengan `jlink` (modul dihitung dari `jdeps` terhadap `vandebooth.jar` + `java.desktop`, `java.logging`, `java.naming`, `jdk.httpserver`, `java.prefs`, `java.sql` bila perlu) ke `resources/runtime`; JAR ke `resources/vandebooth.jar`. Mode paket memakai runtime ini, mode dev memakai Java sistem. Target ukuran installer <= 250 MB; laporkan ukuran sebenarnya.
- Data tetap di `~/VanDeBooth`; **uninstall tidak menghapus data** (tulis di README).
- Flag `--kiosk` (fullscreen tanpa menu) dan F11 (toggle fullscreen). Dokumentasikan di README.
- **Tidak** menandatangani kode dan tidak ada auto-update (di luar cakupan). README wajib jujur: Windows SmartScreen akan menampilkan peringatan "unrecognized app"; langkahnya "More info -> Run anyway".

## 2. Verifikasi paket (otomatis)
- Build `--dir` (tanpa installer) lalu jalankan e2e Playwright-Electron terhadap aplikasi terpaket: sidecar naik dengan runtime bundel, `/health` ok, alur Attract -> Result lulus.
- Jika memungkinkan: pasang installer secara senyap ke folder sementara (`Setup.exe /S /D=<folder>`), jalankan smoke test yang sama, lalu copot. Jika terblokir di mesin ini, laporkan dan lewati.
- Hitung `SHA256` installer ke `SHA256SUMS.txt`.

## 3. Aset uji dan screenshot
- Pindahkan `docs/assets/test-feed.mjpeg` ke `ui/e2e/fixtures/`. Semua e2e (dan Electron) memakai `--use-file-for-fake-video-capture=<file>` menggantikan pola hijau bawaan. Bila Chromium menolak formatnya, ubah ke `.y4m` kecil dan laporkan.
- Hasilkan ulang **seluruh** screenshot di `docs/screenshots/` (1920x1080) dengan feed ini. Semua layar tamu (Attract, Layout, Pay, Capture, Review, Filter, Result) dan panel operator (Sharing, Gallery). Ini bahan README dan media LinkedIn. Di README beri keterangan: "Screenshots use a synthetic test video feed."

## 4. README final (Inggris, maks sekitar 150 baris, jujur terhadap kode)
Urutan: banner (screenshot Attract) · satu paragraf apa itu Van de Booth · fitur (hanya yang ada: alur 6 layar, 3 layout, 4 filter, pembayaran demo opsional, cetak 4x6 single/two-up, QR lewat jaringan lokal, Mode Operator dengan PIN, galeri, offline-first) · galeri screenshot (4-6 gambar) · instalasi (Releases, SmartScreen, firewall jaringan privat) · panduan singkat operator · arsitektur (diagram Mermaid: Electron -> React UI -> Java sidecar -> disk; ShareServer terpisah) · pola desain (**hitung ulang dari kode**; jangan menyalin angka lama) · tes (jumlah yang terukur) · pengembangan (perintah dev/build/test/e2e/package) · riwayat proyek: "Started as a 3-person OOP course project (Umem, Ibnu, Ihsan). Version 2 is a product rebuild: new UI, operator mode, sharing, printing." · lisensi MIT · kredit (Fraunces dan Plus Jakarta Sans: SIL OFL; catatan status shutter.wav dari ASSETS.md). Tambahkan `docs/ARCHITECTURE.md` dengan diagram komponen dan diagram kelas Mermaid yang sesuai kode terbaru.

## 5. CI (GitHub Actions)
`.github/workflows/ci.yml`: JDK 21 + Node LTS; `./mvnw -q verify`; `npm ci && npm test`; e2e Chromium (bukan tes Electron) dengan feed uji. Setelah push, pantau hasilnya (`gh run watch`); perbaiki sampai hijau, maksimal 3 percobaan, lalu laporkan jika masih merah. Tambahkan badge CI ke README hanya setelah hijau.

## 6. Release (DRAFT saja)
- Tag `v2.0.0` pada commit final. Buat **draft release** dengan installer + `SHA256SUMS.txt` dan catatan rilis Inggris: apa yang baru dibanding v1, cara pasang, keterbatasan yang diketahui (tanpa tanda tangan kode, hanya Windows, pembayaran hanya demo, berbagi hanya di jaringan lokal, aset suara shutter belum berlisensi). **Jangan publish**; Umem yang menerbitkan.
- Perbaiki catatan rilis `v1.0.0`: tambahkan di awal "Legacy Swing version, superseded by v2.0.0" dan koreksi klaim yang tidak sesuai kode (jumlah pola desain, template, filter; Google Drive sudah dihapus). Ini mengubah teks publik; lakukan lewat `gh release edit`.
- `gh repo edit`: deskripsi "Offline-first photobooth for events. React + Electron UI, Java engine, touch-friendly flow, local-network QR sharing, printing, and operator mode." dan topik `photobooth, electron, react, java, kiosk, typescript, offline-first, event-tech`.

## 7. Selesai jika
`./mvnw -q verify`, `npm test`, e2e, e2e terhadap build terpaket semuanya hijau; installer terbangun dan ukurannya dilaporkan; screenshot baru dengan feed uji ada; README dan ARCHITECTURE.md final; CI hijau (atau dilaporkan); draft release v2.0.0 ada; git status bersih, commit di main dan di-push.
