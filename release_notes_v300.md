# LW Launcher v3.0.0

## Smooth Paging & Navigation Overhaul
- **New `SmoothHomePager` controller**: Replaces native `HorizontalScrollView` coasting (`OverScroller`) and delayed `16ms` midpoint snapping with deterministic `VelocityTracker` fling detection (`resolveTargetPage`) and a GPU-layered quintic ease-out snap animator. Swiping back from Page 2 to Page 1 (even on quick short flicks) now switches cleanly every time.
- **Zero-relayout page dots & scroll listeners**: Eliminated `removeAllViews()` layout thrashing inside `layout_page_dots` during page transitions; dots reuse fixed `8dp` slots in a `20dp` fixed-height strip and skip unnecessary `updateDragPageChips` / `updateActivePageWindow` work when not dragging.
- **All v2.0.6 features preserved**: Original Google Search widget with Up/Down/Remove snapping, unified grid Calendar & widget dragging, drag-and-drop dock, grouped accordion widget picker, Material 3 Home Settings, and top-resetting App Drawer.
