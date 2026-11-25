## LW Launcher v3.0.2 (Target SDK 36 / Play-Ready)

### Download & Install:
- 📱 **`LW_Launcher_v3.0.2.apk`** *(226 KB)* — **Recommended for Android Phones**: Tap to install/update directly on your device.
- 📦 **`LW_Launcher_v3.0.2.aab`** *(143 KB)* — **Google Play App Bundle**: For publishing to Google Play Store.

---

### Highlights Since v2.0.1
- **Smooth Multi-Page Navigation (`SmoothHomePager`)**: Replaced native `HorizontalScrollView` coasting and `16ms` delayed snaps with deterministic `VelocityTracker` flick/drag resolution, GPU-layered quintic ease-out page transitions, and non-relayouting page dots—fixing Page 2 → Page 1 snaps and frame drops.
- **Interactive Mid-Air App Drawer (`AppDrawerController`)**: Tracks your finger 1:1 with real-time `translationY`, opacity, and home screen zoom/fade so you can hold the drawer mid-air at any height while opening or closing, catch in-flight animations, and scroll up/down inside `GridView` with hard flicks without accidentally closing. Always opens cleanly at the top (`gvApps.setSelection(0)`).
- **Uniform Pixel Circular Icons (`IconNormalizer`)**: Normalizes all app icons (AdaptiveIconDrawable, square/rounded-square legacy bitmaps, circular icons, and irregular cutouts) into anti-aliased `160×160` Pixel circles across Home Pages, App Drawer, Dock, and Folders.
- **Unified Workspace & Widgets**:
  - Calendar (*At a Glance*) and Android AppWidgets live on a unified `cols × rows` cell grid with free cross-page drag/repositioning, live ghost cell previews, and **Remove** bin drops.
  - Original Google Search Bar widget snaps strictly to **Top (Up)**, **Bottom (Down)**, or **Remove** bin.
  - Grouped expandable/collapsible Accordion Widget Picker (`dialog_widget_picker.xml`) & Material 3 Home Settings (`dialog_home_settings.xml`).
  - Pure drag-and-drop Dock customization with fluid drag cards and hover lift animations.

### Release Assets Included
- `LW_Launcher_v3.0.2.apk` — Direct install APK for Android
- `LW_Launcher_v3.0.2.aab` — Google Play App Bundle
- `LW_Launcher_v3.apk` / `LW_Launcher.apk` — Convenience aliases
- `LW_Launcher_v3.aab` / `LW_Launcher.aab` — Convenience aliases
