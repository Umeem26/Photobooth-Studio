# Changelog

All notable changes to Van de Booth. Dates are release dates (YYYY-MM-DD); versions follow [Semantic Versioning](https://semver.org/).

## [2.1.1] - 2026-10-08

### Added
- Operator Mode > Photos > **Mirror photos** (config `photos.mirror`, default on). It applies to new photos; the live preview is always mirrored.

### Changed
- The window now shows the Van de Booth icon instead of the default Electron icon.
- Saved photos are mirrored so they match the live preview (turn off with the new toggle).
- The repository moved to `Umeem26/Van-de-Booth`; links and badges are updated.

## [2.1.0] - 2026-10-08

### Added
- Six strip layouts instead of three: Classic Strip (2x6, 4 photos), Tall Strip (2x6, 3), Wide (6x4, 3), Big Shot (6x4, 1), Four Square (6x4, 4) and Contact Sheet (4x6, 6).
- Layouts are data (canvas size, photo cells, footer area) in one place in the Java engine. `GET /api/layouts` returns `canvas`, `cells`, `footer` and `paper`, and the Layout screen draws each card's miniature from it.
- Operator Mode > Event > **Layouts offered**: choose which layouts guests see (at least one stays on; default all six).
- Compose crops each photo to its cell with a bias toward the top (heads) and never discards more than 12.5% per side; unit tests enforce it.
- `docs/screenshots/layouts/`: real compose output for every layout and a `layouts-overview.png` sheet; screenshots of every operator panel.
- `CHANGELOG.md`, `CONTRIBUTING.md`, GitHub issue templates and a social preview image.

### Changed
- **Wide** (`horizontal-3`) is redesigned: one large hero photo and two stacked close-ups, wordmark bottom left, event caption bottom right. Layout ids are unchanged.
- Layout screen shows all layouts as a 3x2 grid without scrolling; with fewer layouts offered the cards re-center.
- Review screen fits 1 to 6 photos (one big photo, or a grid that fits the screen height); the retake limit still counts per session.
- Filter and Result previews use a fixed container and scale the strip to fit (portrait, landscape and tall strips), never cropped and centered. Controls on the right do not move between layouts.
- Printing: two-up applies to 2x6 strips only. 6x4 layouts print single and landscape on 4x6 paper, Contact Sheet prints single and portrait. The 3 mm margin and aspect ratio are unchanged.
- Strips are composed at 300 DPI canvas sizes (for example 600x1800 for a 2x6 strip).

### Known limitations
Unchanged from 2.0.0: not code-signed (SmartScreen warns), no auto-update, Windows x64 only, demo payment only, sharing on the local network only, `shutter.wav` license unknown and unused.

## [2.0.0] - 2026-10-08

Full rebuild of the v1 Swing photobooth into an offline-first event kiosk: React touch UI in Electron on top of the Java engine.

### Added
- Six-screen guest flow (Attract, Layout, Capture, Review, Filter, Result) with single-photo retakes and idle timeouts.
- Three strip layouts, four filters, event name and date in the strip footer.
- Printing on 4x6 paper (single or two-up) with a copy limit per guest.
- QR download over the local network with expiring links; photos never leave the PC.
- Operator Mode behind a PIN: event, photo timing, payment, sharing, printing, gallery (ZIP export, cleanup) and status.
- Optional demo payment screen, Windows installer with a bundled Java runtime, `--kiosk` flag, GitHub Actions CI.

### Removed
- Google Drive upload from v1.

## [1.0.0] - 2026-01-18

Original Java Swing photobooth ("SixSeven Photobooth Studio Pro"), kept at the tag `legacy-swing-ui`.
