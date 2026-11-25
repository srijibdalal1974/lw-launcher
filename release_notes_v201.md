## LW Launcher v2.0.1 — Translucent Drag Card, Page Vibration & Refined Edge Sensitivity

### 🚀 What's New in v2.0.1:
- **Translucent Drag Card Figure & Subtle 0.93x Zoom Out**:
  - When dragging an app, a visible yet transparent frosted squircle card (`28dp` corners with translucent border) appears framing the page.
  - The page zooms out slightly (0.93x) so you can clearly see adjacent page edges peeking in without obscuring your wallpaper or icons.
  - When drag ends, pages smoothly zoom back to 1.0x with clean elevation dissolution.

- **Haptic Vibration on Page Switch**:
  - Added tactile vibration feedback every time the app switches to another page during drag.
  - Gives clear physical confirmation of page transitions.

- **Refined Edge Scroll Sensitivity (No More Accidental Flipping)**:
  - Dramatically reduced the edge trigger threshold from 85dp down to a focused 36dp (~72px on a 720p screen) right at the glass edge.
  - Added an intentional **500ms continuous hold dwell** before a page switch can occur. Accidental sweeps or dropping icons into columns 1 or 4 will never trigger unwanted page flips.

- **Release Deliverables**:
  - `versionCode="201"`, `versionName="2.0.1"`
  - `targetSdkVersion="36"` (Android 16+) with `minSdkVersion="26"`
  - Release signed with dedicated 2048-bit RSA keystore (`release.keystore`)
  - Includes both **`.aab`** and **`.apk`** in Downloads folders (`LW_Launcher_v2` & `LW_Launcher`).
