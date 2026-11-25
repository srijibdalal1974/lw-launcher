# LW Launcher v3.0.2

## Uniform Circular Pixel Icons
- **New `IconNormalizer`**: Normalizes every launcher app icon on background load into a crisp, anti-aliased Pixel circle (`160×160` px bitmap with `BitmapShader` circular mask):
  - **`AdaptiveIconDrawable` icons**: Renders background and foreground layers with AOSP `108dp/72dp` safe-zone bounds inside a strict circular mask so icons stay circular across any OEM icon theme.
  - **Square / rounded-square legacy icons** (e.g., Free Fire, Harman, Poppy Playtime, Link to Windows): Detects filled corners and either preserves solid edge backdrops inside a padded circle or masks full-bleed artwork cleanly to a circle.
  - **Irregular cutout silhouettes** (e.g., Airtel alert, Content Shell): Centers artwork cleanly inside an adaptive circular plate.
- **Applies uniformly** across the App Drawer, Home Pages, Dock, Folders (including 2×2 thumbnails), and Drag overlays.
