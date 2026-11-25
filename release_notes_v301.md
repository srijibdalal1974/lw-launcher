# LW Launcher v3.0.1

## Interactive App Drawer & Scroll Fixes
- **New `AppDrawerController`**: Tracks your finger 1:1 in real time (`translationY`, opacity, and home screen zoom/fade choreography) so you can pull the app drawer up from Home or pull it down from the top and **hold it mid-air** at any height.
- **Velocity-aware mid-air release**: Releasing after dragging resolves smoothly by fling velocity (`VelocityTracker`) or ~22–25% drag threshold with GPU-layered quintic ease-out animation—catching an in-flight drawer animation mid-air also resumes smooth tracking seamlessly.
- **Fixed accidental drawer close while flicking/scrolling**: Removed the legacy `startY < 650` and loose `firstVisiblePosition == 0` close triggers that shut the app drawer while flicking to scroll up or down; drawer pull-to-close now triggers strictly when pulling down from the search header or when `gvApps` is scrolled strictly to Row 0 top (`firstVisiblePosition == 0 && firstChild.getTop() >= paddingTop`), and locks out closing whenever an upward scroll begins.
- **Home key / gesture handling**: Pressing Home (`CATEGORY_HOME` via `onNewIntent`) cleanly dismisses the open customization panel or app drawer.
