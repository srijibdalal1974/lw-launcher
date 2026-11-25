## LW Launcher v2.0.3 — Proportional Widget Sizing, Grouped Widget Picker, Draggable Search & Calendar, and Material 3 Settings

### 🚀 What's New in v2.0.3:
- **App Drawer Scroll Reset on Reopen**:
  - Fixed scroll persistence bug: opening the app drawer now always resets cleanly to the top row every time, regardless of where you previously scrolled.

- **Proportional Widget Dimensions (Fixed Enormous Clock)**:
  - Fixed bug where clock and other widgets inflated to full screen height.
  - Implemented automatic cell height scaling and `updateAppWidgetSize` constraints (clamped between 72dp and 220dp) so widgets render proportionally and leave ample room for icons below.

- **Draggable & Removable Google Search Bar and At a Glance Calendar**:
  - Long press the Google Search Bar or At a Glance to drag and drop onto the top "Remove" target, removing them from the home screen.
  - Drag and place them onto any home page.
  - Easily toggle them back on/off at any time in the new Home Settings or re-add from the Widget Picker.

- **Modern Grouped App Widget Picker (Expandable Dropdowns)**:
  - Replaced legacy dialog with a modern Material 3 sheet grouping all widgets by application.
  - Each app row shows app icon, name, widget count, and an interactive dropdown accordion arrow.
  - Tap any app to expand and view its widgets with title, dimensions tag (e.g. 2×2, 3×2), and one-tap "Add" button.
  - Includes real-time search filtering across apps and widgets.

- **Material 3 Expressive Home Settings (Replaced 2008 Dialog)**:
  - Complete modern redesign using a frosted rounded bottom card (`#181D24` with 28dp corners).
  - Default Home Launcher card with active status pill and one-tap role prompt.
  - Live toggle switches for Google Search Bar and At a Glance.
  - Interactive Segmented Pills for Home Grid Density (4×5 Standard, 5×5 Expressive, 16×9 Dense).
  - Android System Settings shortcut.
