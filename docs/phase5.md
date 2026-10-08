# Van de Booth: Fase 5 (6 layout, tampilan hasil pas, repo + screenshot)

Melengkapi phase3/phase4. Mockup acuan: `docs/mockups/VanDeBooth-8-layouts.png` (+ `source/layouts.html`). Bingkai di mockup adalah ilustrasi, bukan orang sungguhan.

## Dasar pemilihan (riset)
Panduan penyelenggara booth menyebut strip 2x6 (cetak dua per kertas 4x6) untuk urutan pose dan acara ramai, 4x6 untuk satu foto besar (grup, busana, ruang branding), dan grid multi-pose untuk cerita pendek; keduanya mengingatkan: logo kecil tapi terbaca, area aman agar kepala tidak terpotong, 300 DPI. Sumber: snaptique.ca (2x6 vs 4x6), laphotoparty.com (guide photo booth templates). Pilihan tiga layout dan desainnya adalah rekomendasi (opini) berdasarkan itu; desain digambar sendiri, tidak menyalin template pihak lain.

## 1. Enam layout (canvas px @300 DPI)
| id | nama UI | kertas | foto | sel foto (px) | catatan |
|---|---|---|---|---|---|
| vertical-4 | Classic Strip | 2x6 (600x1800) | 4 | 504x360, gap 24, margin 48 | footer 240 px |
| vertical-3 | Tall Strip | 2x6 (600x1800) | 3 | 504x432, gap 28, margin 48 | footer ~400 px, wordmark lebih besar |
| horizontal-3 | Wide | 6x4 (1800x1200) | 3 | hero 1080x810 di (72,72); dua 528x396 di x=1176, gap 18 | **desain ulang**: hero + dua foto bertumpuk; wordmark kiri bawah, caption kanan bawah |
| postcard-1 | Big Shot | 6x4 (1800x1200) | 1 | 1440x960 di (180,60) | untuk grup/busana; footer satu baris |
| grid-4 | Four Square | 6x4 (1800x1200) | 4 | 660x496, grid 2x2 gap 24 di (60,60) | kolom kanan (x 1404..1800): wordmark bertumpuk + caption, rata tengah |
| grid-6 | Contact Sheet | 4x6 (1200x1800) | 6 | 504x424, grid 2x3 gap 24 di (64,64) | footer ~416 px |
Id lama tetap (`vertical-4`, `vertical-3`, `horizontal-3`); hanya horizontal-3 diubah tampilannya. Default terpilih: vertical-4. Layout didefinisikan sebagai data (ukuran, sel, footer) di satu tempat Java, UI mengambilnya dari `GET /api/layouts` (tambahkan `canvas:{w,h}`, `cells:[{x,y,w,h}]`).
- **Crop**: foto asli tetap 4:3 (disimpan utuh). Saat compose, crop tengah ke rasio sel dengan bias ke atas (kepala) dan **batas buang maks 12,5% per sisi**; uji otomatis menolak sel di luar batas.
- Warna/gaya sama: kertas putih, foto radius kecil, wordmark "Van de Booth" (de italic, oo vermilion), caption `event.name · event.date`. Teks nama layout lewat `strings.ts`.

## 2. Tampilan harus pas
- **Layout screen**: 6 kartu grid 3x2, semua terlihat tanpa scroll di 1920x1080 dan tombol Next >= 96 px. Setiap kartu menggambar miniatur dari `canvas`/`cells` (SVG, bukan gambar tetap), nama, "n photos · paper".
- **Capture/Review**: jumlah foto 1..6. Review: 1 foto = satu foto besar; 2-6 = grid yang muat tinggi layar; tombol Retake >= 96 px; batas retake tetap berlaku total per sesi.
- **Filter dan Result**: pratinjau strip memakai kotak wadah tetap; gambar diskalakan `contain` mengikuti rasio canvas (portrait, landscape, vertikal tinggi), tidak terpotong, tidak menimbulkan scroll, selalu terpusat, bayangan sama. Panel kontrol di kanan tidak bergeser antar layout. Kartu QR dan tombol Result tetap rapi untuk semua rasio.
- **Aturan terukur** (jadi tes): untuk tiap layout di Filter dan Result: kotak gambar sepenuhnya di dalam wadah, sisi pembatasnya terisi >= 90% wadah, `scrollHeight <= 1080`, tidak ada tombol di bawah 96 px (operator 72 px).

## 3. Cetak
`two-up` hanya untuk layout 2x6 (vertical-*). Layout 6x4 dicetak tunggal landscape pada kertas 4x6 (putar otomatis), grid-6 tunggal portrait. Pilihan two-up di panel Printing diberi catatan "Strips only" dan diabaikan untuk layout lain. Margin 3 mm tetap, rasio tidak berubah.

## 4. Operator (rekomendasi, kecil)
Menu Event: "Layouts offered" (centang, minimal 1, default semua). Layout screen hanya menampilkan yang dicentang; bila 1-3 layout, kartu tetap rapi (grid menyesuaikan, tombol Next tetap).

## 5. Repo GitHub dan screenshot
- Bump versi 2.1.0 (`pom.xml`, package.json, README). `CHANGELOG.md` (v2.0.0, v2.1.0).
- Regenerasi `docs/screenshots/` (1920x1080, feed uji sintetis): sisi tamu (Attract, Layout 6 kartu, Pay, Capture, Review, Filter, Result) dan operator (PIN, Event, Photos, Payment, Sharing, Printing, Gallery, Status). Tambah `docs/screenshots/layouts/` berisi **hasil compose nyata** tiap layout (6 PNG) dan satu lembar `layouts-overview.png`.
- README: bagian "Layouts" (overview + tabel singkat), galeri dua kelompok **Guest** dan **Operator** (maks 6 gambar terlihat per kelompok, sisanya di `<details>`), keterangan "Screenshots use a synthetic test video feed.", badge CI/lisensi/rilis. GIF alur tamu (Attract -> Result, <= 8 MB, dari rekaman Playwright + ffmpeg bila tersedia; bila tidak, laporkan dan lewati).
- `docs/brand/social-preview.png` (1280x640, dari screenshot Attract + wordmark) untuk diunggah Umem manual di Settings; `.github/ISSUE_TEMPLATE/` (bug, feature) dan `CONTRIBUTING.md` pendek (cara dev/test, kredit tiga kontributor asli).
- Bangun ulang installer, perbarui `SHA256SUMS.txt`, tag `v2.1.0`, **draft** release (catatan: layout baru, desain ulang Wide, perbaikan tampilan; keterbatasan tetap). Jangan publish. Jangan ubah release v2.0.0.

## 6. Tes dan selesai
- JUnit: tiap layout (ukuran canvas, sel dalam batas, tanpa tumpang tindih, crop <= 12,5%, footer ada, compose < 1,5 s), `/api/layouts` mengembalikan canvas/cells, cetak (6x4 diputar, two-up hanya strip), "Layouts offered" tersimpan dan divalidasi.
- Vitest: reducer untuk n=1..6, daftar layout terfilter.
- Playwright: alur penuh untuk **semua 6 layout** (kamera palsu), aturan terukur bagian 2 di Filter dan Result, screenshot hasil compose.
- Selesai jika: semua suite hijau (JUnit, Vitest, Node, Playwright termasuk terpaket), CI hijau, screenshot dan README final, installer + draft v2.1.0 ada, git bersih, commit di main dan di-push. Laporan <= 15 baris + deviasi.
