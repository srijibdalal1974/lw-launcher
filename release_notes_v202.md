## LW Launcher v2.0.2 — Fluid 60fps Page Carousel Animations & Organic Drag Physics

### 🚀 What's New in v2.0.2:
- **Fluid Hardware-Synchronized Page Carousel Scrolling**:
  - Replaced the choppy, rigid native `smoothScrollTo` with a dedicated 60/120fps `ValueAnimator` featuring a custom decelerate curve (`DecelerateInterpolator(1.8f)`).
  - Page transitions now glide smoothly across the screen without frame drops or jarring pauses.

- **Dynamic Card Depth Scaling & Parallax Transitions**:
  - When changing pages during drag or window mode, the incoming active page smoothly expands forward (`0.88x -> 0.94x`, `alpha: 0.80 -> 1.0`, elevation `2dp -> 10dp`).
  - The outgoing page gracefully recesses back into the carousel (`0.94x -> 0.88x`, `alpha: 1.0 -> 0.80`, elevation `10dp -> 2dp`).
  - Creates the authentic Android 17 / Pixel Launcher 3D card carousel effect.

- **Organic Drag Velocity Tilt Physics**:
  - The floating dragged icon now features live physics responding to drag velocity.
  - Swift horizontal finger movement naturally tilts the icon up to ±14° in the direction of travel, smoothly relaxing back to 0° when resting or releasing.
  - Makes moving objects feel responsive, lightweight, and alive.

- **Instant Zero-Lag Page Snapping**:
  - Swiping between pages now snaps immediately with single-frame (16ms) scheduling instead of a 100ms delay.
  - Perfectly tuned edge hold dwell (420ms) ensures prompt page flips when holding at the edge while preventing accidental triggers.

- **Build & Packaging**:
  - `versionCode="202"`, `versionName="2.0.2"`, `targetSdkVersion="36"` (Android 16+).
  - Release signed with release keystore (`release.keystore`).
  - Universal `.apk` and Google Play `.aab` delivered to Downloads.
