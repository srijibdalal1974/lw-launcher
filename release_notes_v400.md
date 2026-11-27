## LW Launcher v4.0.0 — Pixel Morph Transitions, Auto-Max Refresh Rate & Full-Color Search Capsule

### Download & Install
- 📱 **`LW_Launcher_v4.0.0.apk`** *(399 KB)* — Recommended universal APK for direct installation on Android 11–17 devices (`minSdk 30`, `targetSdk 36`).
- 📦 **`LW_Launcher_v4.0.0.aab`** *(228 KB)* — Google Play App Bundle built and validated with `bundletool`.

---

### What's New in v4.0.0
- **Continuous Pixel-Style App Launch Morph (`LaunchTransitionView` + `IconNormalizer`)**:
  - Replaced abrupt scale jumps with Material 3 2-segment emphasized container curves, unified cold/warm visibility, separated `AdaptiveIconDrawable` foreground/background parallax layers, per-app splash surface tinting, ambient elevation shadows, and seamless handoff (`fgAlpha` dissolve + centered final bounds) into opening apps.
  - Added background pre-warming of Dock and Home Page 0 launch layers for fast cold first taps.
- **Auto-Adaptive Maximum Refresh Rate (`DisplayRefreshHelper`)**:
  - Hidden the manual Animation Frame Rate selector in Home Settings; LW Launcher now automatically detects supported display modes (60 / 90 / 120 / up to 180 Hz) and dynamically locks animations, scrolls, drags, popups, and app launches to your display's maximum supported refresh rate on interaction.
- **Full-Color Original Google Logos in App Drawer Search Capsule**:
  - Restored authentic 4-color **Google G**, **4-color Mic**, and **4-color Lens** vector logos inside the drawer search bar (`iv_drawer_search_icon`, `btn_drawer_mic`, `btn_drawer_lens`) with direct launches to Google Search, Voice Search, and Google Lens alongside inline app + web filtering.
- **Swipe-vs-Tap Isolation & Drawer Polish**:
  - Added strict movement slop (`12dp`) and diagonal swipe paging thresholds so horizontal/vertical swipes over app icons never accidentally open apps.
  - Synchronized 120 Hz pop-in animations for long-press app menus, folders, customization panel, and bottom sheets, plus improved frosted SurfaceFlinger blur in the App Drawer.
- **Standalone At a Glance Weather Sync (`LiveWeatherController`)**:
  - Added real-time `ConnectivityManager.NetworkCallback` auto-sync across Wi-Fi and cellular networks on all Android 11–17 devices (including non-Pixel phones) with background-data policy diagnostics.
- **Repository Housekeeping**:
  - Added standard Apache 2.0 `LICENSE` and updated `README.md` for v4.0.0.

### Release Assets Included
- `LW_Launcher_v4.0.0.apk` — Signed universal Android APK (`408,594` bytes)
- `LW_Launcher_v4.0.0.aab` — Signed Google Play App Bundle (`233,910` bytes)
- `LW_Launcher.apk` / `LW_Launcher.aab` — Latest convenience aliases
