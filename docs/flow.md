# Van de Booth: Alur Tamu (v1)

Cakupan **Fase 2**: Attract -> Layout -> Capture -> Review -> Filter -> Result (simpan lokal). Fase 3 menambah: layar Payment (simulasi) setelah Layout, tombol Print, QR jaringan lokal, Mode Operator. Stiker ditunda (belum dijadwalkan). Mockup tersedia untuk Attract, Layout, Capture, Result; Review dan Filter mengikuti design system yang sama (kartu paper, grid foto 4:3, satu tombol primary).

State machine tunggal (reducer, tanpa library tambahan): `attract -> layout -> capture -> review -> filter -> result -> attract`. Satu sesi = satu `sessionId` dari sidecar. Sesi selalu tersimpan di disk (SessionRepository).

## Layar
1. **Attract**: preview kamera live (cermin) di kartu kanan, strip dekoratif, tombol "Start photos". Memulai = `POST /api/sessions` lalu ke Layout. Kamera diminta sekali di sini; jika gagal tampilkan Error.
2. **Layout**: 3 kartu dari `GET /api/layouts` (default terpilih: yang pertama). "Next" -> Capture. "Back" -> Attract (sesi dibatalkan).
3. **Capture**: kamera layar penuh (cermin), `n` foto sesuai layout. Per foto: chip "Photo i of n" + titik progres, CountdownDisc 3-2-1 (1 detik tiap angka), flash, ambil frame, thumbnail kecil muncul 1,2 detik di kiri bawah, lanjut foto berikutnya (jeda 1 detik). Caption berganti tiap foto. Tombol ghost "Cancel" kecil di pojok kiri atas, konfirmasi sebelum membuang sesi.
4. **Review**: grid foto hasil (4:3) dengan tombol "Retake" di tiap foto, maksimal `maxRetakes` (default 2) per sesi di semua foto. Retake satu foto = kembali ke Capture hanya untuk foto itu (countdown lagi), lalu kembali ke Review. "Looks good" (primary) -> Filter. "Start over" (ghost) -> Layout dengan sesi baru.
5. **Filter**: kiri strip komposit besar (hasil `compose`), kanan 4 pilihan (Original, Black & white, Vintage, Warm) sebagai kartu besar dengan swatch. Ganti pilihan memanggil compose ulang (debounce 150 ms); tampilkan skeleton jika respons > 300 ms. "Next" -> Result.
6. **Result**: layout mockup layar 4. Fase 2: tampilkan hanya "Save photos" (outline), "Retake" (ghost, mulai sesi baru dengan layout yang sama dari Capture), dan hitung mundur 45 detik kembali ke Attract. Tombol Print dan kartu QR **tidak ditampilkan** di Fase 2 (disiapkan di Fase 3). "Save photos" memanggil export lokal dan menampilkan toast dengan path.

## Aturan
- **Idle**: 90 detik tanpa sentuhan di Layout/Review/Filter -> kembali ke Attract, sesi belum selesai ditandai `abandoned` di meta.
- **Kamera**: resolusi ideal 1920x1080, fallback otomatis. Foto disimpan **dicermin** agar sama dengan preview (yang juga dicermin). Crop tengah ke 4:3. JPEG kualitas 0,92.
- **Error**: kamera hilang/ditolak -> layar Error "Camera not found..." + "Try again"; sidecar tidak merespons -> "The booth service is not responding." + "Try again" (cek `/health`); gagal compose/export -> toast, state tidak berubah.
- **Privasi**: foto hanya di disk lokal (`~/VanDeBooth`). Tidak ada unggahan.
- **Keyboard cadangan** (untuk uji tanpa layar sentuh): Enter = tombol primary, Esc = Cancel/Back.
