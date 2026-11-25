## LW Launcher v1.0.3 — Google Play Ready (.AAB & Android 16 TargetSDK)

### 📦 Deliverables & Updates:
- **Android App Bundle (.aab)**: Full Google Play-compliant bundle built with `bundletool` and proto-format AAPT2.
- **Updated Target SDK**: Upgraded `targetSdkVersion` to **36** (Android 16) with `minSdkVersion=26`.
- **Dedicated Release Keystore**: Release signing with 2048-bit RSA release keystore (`release.keystore`).
- **Default Home Role Prompt**: Modern `RoleManager` (API 29+) integration with graceful fallback for seamless system home assignment.
- **Dual Release Assets**: Includes both `.aab` for Google Play Console upload and `.apk` for direct offline installation.
