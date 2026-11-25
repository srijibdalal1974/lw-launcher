## LW Launcher v2.0.5 — Responsive Search Bar Dragging & 3-Zone Dynamic Page Placement

### 🚀 What's New in v2.0.5:
- **Instant Search Bar Long-Press Dragging**:
  - Fixed touch event absorption by Google AppWidgetHostView in the search container.
  - Implemented raw window touch event dispatching (`dispatchTouchEvent`) with a 300ms hold detector, ensuring holding down anywhere on the Google Search Bar immediately lifts it into drag mode with tactile haptic vibration.
  - Long-press listener attached directly to `searchWidgetHostView` as an additional guarantee.

- **3-Zone Free Page Placement (Top, Middle, Bottom)**:
  - Re-architected each launcher page into three dynamic placement zones:
    1. **Top Zone** (above clock and widgets)
    2. **Middle Zone** (between widgets and app grid)
    3. **Bottom Zone** (below app grid, above the dock)
  - Dropping the **Calendar** or **Google Search Bar** now respects where your finger is released:
    - Upper screen (`< 35% height`) -> Placed in **Top Zone**!
    - Mid screen (`35% - 70% height`) -> Placed in **Middle Zone** (it NO LONGER jumps back to the top!)
    - Lower screen (`> 70% height`) -> Placed in **Bottom Zone**!
  - Pinning to top header: Drop at the very top edge (`< 90dp`) to pin back to the system top header.
  - Docking at bottom: Drop at the bottom edge above dock to dock back above the dock.
