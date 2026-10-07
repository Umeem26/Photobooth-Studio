# Van de Booth: Fase 3 (Mode Operator, Berbagi, Cetak, Pembayaran Demo)

Melengkapi `design-system.md`, `flow.md`, `architecture.md`. Mockup baru: `docs/mockups/VanDeBooth-6-pembayaran.png` dan `-7-operator.png` (+ HTML di `source/`). Layar Result mengikuti `VanDeBooth-4-hasil.png` penuh (Print + kartu QR sekarang ditampilkan). Teks UI Inggris lewat `strings.ts`. Semua aturan design system tetap berlaku; kontrol operator minimal **72 px** (bukan layar tamu), kontrol tamu tetap >= 96 px.

## 1. Pembayaran demo (opsional, default OFF)
- Config `payment.enabled=false`, `payment.price=25000`. Bila ON, alur: Attract -> Layout -> **Pay** -> Capture ... StepPill menjadi 4 (Layout, Pay, Photos, Result).
- Layar Pay (mockup 6): harga format `Rp 25.000`, QR palsu (isi teks `DEMO-PAYMENT-NOT-REAL`), chip kuning "Demo mode · no real payment" **wajib selalu tampil**, teks "Waiting for payment…". Tombol "Simulate payment" -> lanjut ke Capture. Tanpa logo atau merek QRIS/e-wallet asli. Tidak ada panggilan jaringan, tidak ada gateway. Antarmuka `PaymentProvider` di backend (`DemoPaymentProvider` mengembalikan "paid" saat tombol ditekan) agar gateway asli bisa dipasang kelak.
- Backend: `POST /api/sessions/{id}/payment` -> `{status:"pending"}`; `POST /api/sessions/{id}/payment/simulate` -> `{status:"paid"}`; `GET .../payment` -> status. Capture ditolak 402 bila `payment.enabled` dan status belum `paid`.

## 2. Berbagi lewat jaringan lokal (QR)
- Server **terpisah** dari API sidecar: `ShareServer` bind ke semua antarmuka (port `share.port`, default 8080), hanya baca, tanpa direktori listing, tanpa API lain. API utama tetap 127.0.0.1 + token.
- `GET /s/{shareToken}`: halaman unduh sederhana bergaya design system (inline CSS, tanpa JS eksternal) menampilkan strip + tombol "Download strip" dan tiap foto. `GET /s/{shareToken}/strip.png`, `/photo/{i}.jpg`. `shareToken` = 16 byte acak base64url per sesi, kedaluwarsa `share.expiryHours` (default 6), 404 setelah itu. Rate limit sederhana per IP.
- `POST /api/sessions/{id}/share` -> `{url, qrPng}` (QR via ZXing, base64). Host = IPv4 privat non-loopback terdeteksi; `share.host` bisa menimpa. `share.enabled` default true.
- Result: kartu QR (400x560) "Scan to download" + "Join the booth Wi-Fi, then scan this code." Bila sharing nonaktif atau tidak ada jaringan, kartu diganti teks "Sharing is off." dan tombol lain bergeser rapi.
- Catatan operasional (tampil di panel Sharing): tamu harus di Wi-Fi/hotspot yang sama; Windows akan meminta izin firewall jaringan privat.

## 3. Cetak
- `PrintExportStrategy` (`javax.print`), strategi ke-2 di samping Local. Config: `print.printer` (kosong = default sistem), `print.maxCopies=2`, `print.layout=single|two-up` (two-up = dua strip berdampingan pada kertas 4x6 untuk dipotong), `print.paper=4x6`. Skala ke area cetak dengan margin 3 mm, tanpa mengubah rasio.
- API: `GET /api/printers` -> `[{name, default, status}]`; `POST /api/sessions/{id}/print {copies}` -> `{queued:true}` (async, status lewat polling `GET .../print`: `queued|printing|done|failed`).
- Result: tombol primary "Print strip". Saat mencetak: "Printing…" dan hitung mundur 45 detik **dijeda**. Selesai: toast "Sent to the printer". Gagal: "Printer not found. Ask the host for help." Batas `maxCopies` per sesi: setelah tercapai tombol nonaktif dengan "Print limit reached".
- Tanpa printer fisik: tes memakai `PrintService` palsu; tambahkan mode `print.mode=file` yang menulis PNG siap-cetak ke `<output>/print-queue/` (untuk uji dan demo).

## 4. Mode Operator
- **Akses**: tekan dan tahan wordmark di Attract 3 detik -> dialog PIN. Pertama kali (belum ada PIN): dialog "Create a PIN" (4-8 digit, konfirmasi). **Jangan ada PIN bawaan di kode/repo.** PIN disimpan hash (PBKDF2 + salt) di folder config pengguna. 5 salah berturut-turut -> kunci 30 detik. `POST /api/admin/login {pin}` -> token admin (memori, kedaluwarsa 15 menit tanpa aktivitas), header `X-Admin-Token` untuk semua `/api/admin/*`.
- **Tata letak** (mockup 7): sidebar 430 px (wordmark, "OPERATOR MODE", 7 menu), konten kartu paper, judul Fraunces 84, "Exit operator mode" di kanan atas. Menu:
  1. **Event**: nama event, tanggal (muncul di footer strip dan chip Attract).
  2. **Photos**: `maxRetakes`, detik countdown (2-5), jeda antar foto.
  3. **Payment**: toggle demo, harga.
  4. **Sharing**: toggle, alamat jaringan (terdeteksi + override), kedaluwarsa (1 h / 6 h / 24 h), pratinjau QR uji.
  5. **Printing**: pilih printer, salinan maksimal, single/two-up, tombol "Print test page".
  6. **Gallery**: grid thumbnail sesi (status, waktu), hapus satu sesi (dengan konfirmasi), "Export all" (ZIP ke `<output>/exports`), hapus sesi lebih tua dari N hari (konfirmasi).
  7. **Status**: kamera terdeteksi (nama), printer, versi, ruang disk kosong, alamat berbagi, tombol "Copy diagnostics".
- API: `GET/PUT /api/admin/config` (validasi nilai, tulis `config.properties` secara atomik), `GET /api/admin/sessions`, `DELETE /api/admin/sessions/{id}`, `POST /api/admin/export-all`, `POST /api/admin/purge {olderThanDays}`, `GET /api/admin/status`. Perubahan config berlaku tanpa restart untuk event/photos/payment/print; share.port butuh restart ShareServer otomatis.
- Idle operator: 5 menit tanpa aktivitas -> kembali ke Attract dan token dicabut.

## 5. Teks tambahan
"Scan to pay" / "ONE SESSION, 4 PHOTOS" / "Waiting for payment…" / "This screen updates by itself." / "Simulate payment" / "Create a PIN" / "Enter PIN" / "Wrong PIN. Try again." / "Too many attempts. Wait 30 seconds." / "Exit operator mode" / "Saved." / "Delete this session?" / "Delete" / "Cancel" / "Sharing is off." / "Print limit reached" / "Sent to the printer".

## 6. Tes dan selesai
- JUnit: tiap endpoint baru (payment 402, share token + kedaluwarsa + 404, rate limit, admin login + lockout, config validasi, gallery hapus/purge, export-all ZIP, print dengan PrintService palsu dan mode file). Pastikan ShareServer tidak mengekspos path di luar sesi (uji path traversal).
- Vitest: reducer untuk alur dengan dan tanpa payment, hitung mundur Result yang dijeda.
- Playwright (kamera palsu): alur penuh dengan payment ON dan OFF; Result menampilkan Print + QR; masuk operator (buat PIN, ubah event name dan lihat footer berubah di strip), galeri hapus; screenshot tiap layar baru ke `docs/screenshots/`, dibandingkan dengan mockup 4, 6, 7.
- Keamanan: `grep` memastikan tidak ada PIN bawaan atau secret di repo; ShareServer terbukti hanya melayani token valid.
