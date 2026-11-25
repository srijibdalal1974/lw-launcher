@echo off
set "PATH=C:\Users\sriji\Downloads\platform-tools-latest-windows\platform-tools;C:\Users\sriji\Downloads\mingit\cmd;C:\Users\sriji\Downloads\gh;%PATH%"
cd /d C:\Users\sriji\.gemini\antigravity\scratch\pixel_launcher_custom

echo [1/4] Building release v2.0.1 (.aab and .apk)...
python build_release.py
if %errorlevel% neq 0 (
    echo Build failed!
    exit /b %errorlevel%
)

echo [2/4] Installing v2.0.1 APK on connected phone via ADB...
adb install -r release\LW_Launcher_v2.apk
adb shell am start -n com.custom.pixellauncher/.MainActivity

echo [3/4] Committing to Git and pushing to GitHub...
git add -A
git commit -m "LW Launcher v2.0.1: Translucent drag card figure, subtle 0.93x zoom out, page switch vibration, and 36dp/500ms edge sensitivity"
git push origin main

echo [4/4] Creating GitHub Release v2.0.1...
gh release create v2.0.1 "release\LW_Launcher_v2.aab" "release\LW_Launcher_v2.apk" "release\LW_Launcher.aab" "release\LW_Launcher.apk" --title "LW Launcher v2.0.1 - Translucent Drag Card & Refined Edge Sensitivity" --notes-file "release_notes_v201.md"
if %errorlevel% neq 0 (
    echo Failed to create GitHub release v2.0.1
    exit /b %errorlevel%
)

echo Release v2.0.1 created successfully!
gh release view v2.0.1
