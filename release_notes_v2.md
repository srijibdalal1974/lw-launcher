## LW Launcher v2.0.0 — Pixel Launcher Drag & Android 17 Morphing Folder

### 🚀 What's New in v2.0.0:
- **Pixel Launcher Drag Clarity (Zero Blur)**:
  - Completely removed the dark overlay scrim and background render effect blur during icon drag.
  - The home screen remains at 100% full scale (1.0x true scale) so the wallpaper, grid slots, and adjacent apps remain crystal clear.
  - Hovering over an empty slot now highlights the exact drop target cell (`drop_target_bg`), allowing precise placement with zero guesswork.
  - Hovering over an existing app gently scales it up (1.15x) indicating instant folder merge.

- **Android 17 Morphing Folder Expansion**:
  - Folders now open **directly near / over where the folder icon is**, rather than popping up in the screen center.
  - Added Android 17 spring morph animation (`OvershootInterpolator(1.15f)`) originating directly from the relative pivot point of the folder icon.
  - Smoothly clamps to safe viewport boundaries (clearing status bar and dock/navigation bar).

- **Google Play & Delivery Upgrades**:
  - `versionCode="200"`, `versionName="2.0.0"`
  - `targetSdkVersion="36"` (Android 16+) with `minSdkVersion="26"`
  - Release signed with dedicated 2048-bit RSA keystore (`release.keystore`)
  - Includes both **`.aab`** (Google Play Store bundle) and **`.apk`** (direct installation).
