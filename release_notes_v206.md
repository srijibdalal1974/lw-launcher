## LW Launcher v2.0.6 — Original Google Search Bar (Up/Down Only) & Unified Grid Placement for Calendar and Widgets

### 🚀 What's New in v2.0.6:
- **Original Google Search Bar Only (Strictly Up, Down, or Remove)**:
  - Removed the custom per-page fallback bar (`"Search apps & web..."`); the authentic Google App Search Widget (`AppWidgetHostView`) now moves directly between the **Top (Up)** and **Bottom (Down)** positions.
  - Long-press and drag into the upper half to snap **Up**, lower half to snap **Down** (above the dock), or drop onto the **Remove** bin to hide.

- **Unified Pixel-Grid Overlay for Calendar & All Widgets**:
  - Re-architected each page so Calendar (At a Glance) and every AppWidget (like Clock) sit directly on the `cols × rows` cell coordinate grid instead of a collapsing top header container.
  - Calendar (`3×1` / `5×2` on dense grids) and Widgets (`2×2`, etc.) can now be dropped on **any row and column** (Row 1, Middle, Bottom, Left/Right, or any page) and stay right where placed.
  - Added a live translucent cell preview outline during drag so you can see the exact slot before dropping, with automatic collision avoidance that shifts app icons out of the way cleanly.
  - Added root touch hold detection for placed widgets and Calendar so you can pick up widgets even by pressing centered interactive areas (like Clock faces).
