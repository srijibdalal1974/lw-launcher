## LW Launcher v2.0.4 — Free-Form Widget Dragging, Native Dock Drag & Drop & Calendar Fixes

### 🚀 What's New in v2.0.4:
- **Free-Form Draggable & Movable Widgets**:
  - Any placed widget (including clocks, system widgets, custom widgets) can now be freely dragged across pages!
  - Added an elegant Material 3 drag handle (`ic_move`) to every widget. Long-pressing the widget or the handle enters drag mode with smooth tilting physics and page navigation.
  - Drop on another page or page chip to move it there.
  - Drop onto the top red "Remove" trash bin or tap the handle to remove it cleanly.

- **Native Dock Drag & Drop (No More Select App Popups)**:
  - Long-press any app in the dock to lift and drag it to any home screen page, another dock slot, or the Remove/Uninstall bins.
  - Drag any app from any home page or app drawer and drop directly onto any dock slot (0–4).
  - Empty dock slots now feature subtle Material dots without old popup dialogs.
  - Dropping an app into an occupied dock slot smoothly swaps the items.

- **Fixed Calendar Preview & Collision Bug**:
  - Replaced the erroneous Google Lens icon preview with a crisp Material 3 Calendar icon (`ic_calendar`).
  - Fixed page pruning and widget clearing bug: moving the Calendar or Search Bar now preserves all other widgets on the screen without wiping or resetting them.
  - Pinned header docking: Drag Calendar to the top header (`< 140dp`) to pin it back to the top; drag Google Search Bar near the dock (`> screenHeight - 160dp`) to dock it at the bottom.
