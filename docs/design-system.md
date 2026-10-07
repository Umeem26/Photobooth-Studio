# Van de Booth: Design System (v1)

Sumber kebenaran visual: `docs/mockups/*.png` (1920x1080) dan HTML aslinya di `docs/mockups/source/*.html` (CSS di dalamnya adalah acuan angka; path font di sana milik mesin penulis, pakai font dari paket npm). Jika dokumen ini berbeda dengan mockup, **mockup menang**.

Bahasa UI: **Inggris saja** (v1). Semua teks lewat satu file `strings.ts` agar mudah dilokalkan nanti.

## 1. Kanvas (Stage)
- Desain pada kanvas tetap **1920x1080**. Komponen `Stage` men-scale kanvas itu (transform: scale) agar muat di jendela mana pun (1366x768, 1920x1080, 4K), letterbox warna `--cream`. Semua ukuran di bawah dalam px pada kanvas ini.
- Teks dan tombol tidak boleh memakai vw/vh. Target sentuh minimal **96 px** pada kanvas.

## 2. Token (CSS variables, satu file `tokens.css`)
| Token | Nilai | Pakai untuk |
|---|---|---|
| `--cream` | #F7EFE2 | Latar layar |
| `--paper` | #FFFBF3 | Kartu, chip |
| `--ink` | #241B16 | Teks utama |
| `--ink-2` | #6B5D52 | Teks sekunder (kontras 5,55:1 di cream) |
| `--verm` | #D9411E | Aksi utama, accent. Teks putih/paper di atasnya (4,31:1), hanya untuk teks besar |
| `--butter` | #F4C95D | Sorotan dekoratif |
| `--line` | #E4D6C0 | Garis dan batas |
| `--tint` | #EFE2CC | Latar area preview di kartu |
Aturan: **tanpa gradasi, tanpa emoji**. Vermilion hanya untuk aksi, penanda aktif, dan dua huruf "oo" di logo. Teks kecil tidak boleh berwarna vermilion.

## 3. Tipografi (fon dibundel offline lewat paket npm, jangan memuat dari CDN)
- Judul: **Fraunces** (`@fontsource-variable/fraunces`), weight 560, letter-spacing -0.03em, line-height ~0.98. Frasa kedua judul italic weight 400. Ukuran: hero 150, judul layar 96, hasil 112, subjudul kartu 46.
- UI: **Plus Jakarta Sans** (`@fontsource-variable/plus-jakarta-sans`). Tombol 38 (hero 46) weight 700; teks isi 36 weight 500 warna `--ink-2`; chip 24-34 weight 600; label kecil 19-22 weight 700 uppercase letter-spacing .1em.
- Wordmark: "Van *de* B**oo**th" Fraunces 600, "de" italic weight 400, "oo" `--verm`.

## 4. Bentuk dan bayangan
Radius: kartu 44, area preview 56, kamera layar-penuh 64, pill 999, foto di strip 6. Bayangan: strip `0 18px 40px rgba(36,27,22,.18)`; preview `0 30px 70px rgba(36,27,22,.22)`; kartu terpilih `0 26px 60px rgba(217,65,30,.22)`. Hanya bayangan itu yang dipakai.

## 5. Komponen
- **Button**: tinggi 112 (hero 136), padding horizontal 56 (hero 72), gap ikon 20. Varian `primary` (verm, teks paper), `outline` (border 4 ink, teks ink), `ghost` (teks ink-2, tanpa batas, tinggi 80-84). Satu `primary` per layar. Ikon panah: stroke 2.6.
- **Chip**: tinggi 56 (besar 80-84), radius penuh, `--paper`, border 2 `--line`, teks 24 (besar 32-34).
- **StepPill**: tinggi 64, 3 pil (Layout, Photos, Result). Aktif: latar ink teks paper. Tidak aktif: border 2 line teks ink-2.
- **Card**: `--paper`, border 3 `--line`, radius 44. Terpilih: border 6 verm + bayangan terpilih + badge centang bulat 76 verm di pojok kanan atas (-18,-18).
- **StripPreview**: kertas putih, bayangan strip, foto 4:3, gap 8-14, footer wordmark + caption event. Miring -3,5 deg hanya di layar hasil dan 7 deg di dekorasi layar sambut.
- **CountdownDisc**: lingkaran 380, latar paper 96%, border 14 verm, angka Fraunces 600 ukuran 250. Diletakkan di **kanan** (left 1400, top 330) agar wajah tamu tidak tertutup.
- **ViewfinderCorners**: 4 siku putih (paper) 90 px, border 8, radius 26.
- **QRCard**: kartu 400x560, QR 340 (hitam di putih), judul Fraunces 38, teks 21 ink-2. (Fase 3.)

## 6. Gerak
Durasi: `fast` 120 ms easeOut (tekan), `base` 250 ms easeOutCubic (pergantian layar: fade + geser 8 px), `sheet` 320 ms. Tick countdown: angka scale 1.08 -> 1 dan fade 250 ms tiap detik. Flash saat jepret: overlay putih opasitas 0 -> 0,9 -> 0 dalam 150 ms. Hormati `prefers-reduced-motion` (matikan geser dan scale, pertahankan fade).

## 7. Teks UI (Inggris, sentence case, tanpa tanda seru beruntun)
| Layar | Teks |
|---|---|
| Attract | "One click, *one memory.*" / "Four poses, one strip. Tap the button to start." / tombol "Start photos" / chip "Camera preview" / chip event "{event.name} · {event.date}" |
| Layout | Judul "Pick your *strip.*" / pil "Layout, Photos, Result" / kartu: "Vertical, 4 photos" (The classic booth look.), "Vertical, 3 photos" (Roomier, great for one hero pose.), "Horizontal, 3 photos" (Wide, made for framing or display.) / "Back", "Next" |
| Capture | Chip "Photo {i} of {n}" + titik progres / caption berganti: "Smile! Look at the camera.", "Strike a pose!", "Get closer!", "One more!" |
| Review | Judul "Happy with *these?*" / "Retake" per foto / "Looks good" / "Start over" / bila batas retake habis: "No retakes left" |
| Filter | Judul "Pick a *look.*" / "Original", "Black & white", "Vintage", "Warm" / "Next" |
| Result | "Your strip *is ready.*" / "Save photos" / "Retake" / "Returning to start in {s} seconds" / (Fase 3: "Print strip", "Scan to download", "Join the booth Wi-Fi, then scan this code.") / toast "Saved to {path}" |
| Error | "Camera not found. Check the cable, then try again." / "The booth service is not responding." / tombol "Try again" |

## 8. Daftar larangan
Tidak ada gradasi, emoji, bayangan di luar daftar, warna di luar token, teks vermilion kecil, lebih dari satu tombol primary per layar, animasi mantul, tombol di bawah 96 px, teks bahasa Indonesia di UI v1.
