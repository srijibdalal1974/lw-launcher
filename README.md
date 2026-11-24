# LW Launcher (Lightweight Launcher)

<p align="center">
  <img src="app/res/drawable/ic_launcher.xml" width="128" height="128" alt="LW Launcher Logo" />
</p>

<p align="center">
  <b>An ultra-lightweight, high-performance Android 11–17 home launcher with built-in gesture navigation and an Android 17 style recents multi-task switcher.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_11--17-brightgreen.svg" />
  <img src="https://img.shields.io/badge/Language-Pure_Java-orange.svg" />
  <img src="https://img.shields.io/badge/APK_Size-~120_KB-blue.svg" />
  <img src="https://img.shields.io/badge/License-Apache_2.0-lightgrey.svg" />
</p>

---

## Highlights

- 🪶 **Ultra Lightweight (~120 KB APK)**: Zero Gradle bloat, zero heavy third-party dependencies. Compiles directly using pure Java SDK, AAPT2, and D8.
- ⚡ **Android 17 Gesture Navigation & Recents Switcher**:
  - Built-in bottom gesture navigation bar with high-elevation pill.
  - Upward swipe from bottom triggers the Android 17 squircle card carousel switcher.
  - **Swipe Up to Close**: Fling individual app cards upward to terminate background processes (`killBackgroundProcesses`) with haptic feedback.
  - **Clear All**: One-tap pill to close all background apps and return smoothly to home.
  - Solves the common ColorOS/Realme UI issue where disabling stock launcher breaks `OverviewProxyService`.
- 🎯 **Lawnchair-Style DragLayer (60fps Touch Dragging)**:
  - Real-time elevated floating icon (`1.15x` scale) tracking your finger.
  - Android 17 top drop targets: 🗑️ **Remove from Home** and 🗑️ **Uninstall** with stylized vector trash cans and spring glow animations.
  - Dynamic page chips (`1 2 3 4 5....`) hovering over the blur to drop items directly onto specific pages.
- 📁 **Smart Folder Merging & Auto-Dissolve**:
  - Seamlessly merge icons into folders by dropping one app onto another.
  - Zero duplicates guaranteed (`removeAppFromAllPages`).
  - Auto-dissolves when only 1 item remains in a folder back into a clean home icon.
- 📐 **Dual Grid Density Modes**:
  - **4 × 5 (Pixel Standard)**: Large 56dp icons with generous touch targets.
  - **16 × 9 (Android Power Grid)**: Ultra-dense 144-slot grid with 32dp icons for power users.
- 🎨 **Material You Expressive Design**:
  - 28dp squircle curvature across popups and cards with frosted glass backdrops.
  - Clean edge-to-edge transparent status and navigation bars.
  - Integrated `AppWidgetHost` for native Google Search widgets and app widgets.

---

## Project Structure

```
├── app/
│   ├── AndroidManifest.xml          # Package, permissions, and HOME intent-filters
│   ├── res/                         # Vector drawables, layouts, and styles
│   │   ├── drawable/                # Modern Android 17 squircles & vector icons
│   │   ├── layout/                  # Activity launcher & recents layouts
│   │   └── values/                  # Strings, colors, and themes
│   └── src/
│       └── com/custom/pixellauncher/
│           ├── MainActivity.java    # Core launcher, gestures, recents, drag engine
│           ├── AppInfo.java         # App metadata model
│           ├── AppAdapter.java      # App drawer grid adapter with filtering
│           └── LauncherItem.java    # Desktop grid & folder item state
├── tools/                           # Lightweight standalone build tools (AAPT2, D8)
└── build.bat                        # Lightning-fast pure SDK build script
```

---

## Building from Source

No Gradle or Android Studio required. Build directly via `build.bat`:

```cmd
build.bat
```

The compiled and signed APK will be generated at:
`build\PixelLauncher.apk`

---

## License

This project is licensed under the Apache License 2.0.
