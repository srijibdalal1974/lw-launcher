@echo off
set "PATH=C:\Users\sriji\Downloads\platform-tools-latest-windows\platform-tools;C:\Users\sriji\Downloads\mingit\cmd;C:\Users\sriji\Downloads\gh;%PATH%"
cd /d C:\Users\sriji\.gemini\antigravity\scratch\pixel_launcher_custom

echo [1/5] Building release v2 (.aab and .apk)...
python build_release.py
if %errorlevel% neq 0 (
    echo Build failed!
    exit /b %errorlevel%
)

echo [2/5] Installing v2 APK on connected phone via ADB...
adb install -r release\LW_Launcher_v2.apk
adb shell am start -n com.custom.pixellauncher/.MainActivity

echo [3/5] Capturing verification screenshot...
timeout /t 2 /nobreak >nul
adb exec-out screencap -p > C:\Users\sriji\.gemini\antigravity\brain\266f2d09-f40d-4999-b2d5-11a99397f83b\v2_verified_home.png

echo [4/5] Committing to Git and pushing to GitHub...
git add -A
git commit -m "LW Launcher v2.0.0: Pixel Launcher drag clarity (zero blur), Android 17 morphing folder animation near icon, and versionCode 200"
git push origin main

echo [5/5] Creating GitHub Release v2.0.0...
gh release create v2.0.0 "release\LW_Launcher_v2.aab" "release\LW_Launcher_v2.apk" "release\LW_Launcher.aab" "release\LW_Launcher.apk" --title "LW Launcher v2.0.0 - Pixel Drag & Android 17 Morphing Folder" --notes-file "release_notes_v2.md"
if %errorlevel% neq 0 (
    echo Failed to create GitHub release v2.0.0
    exit /b %errorlevel%
)

echo Release v2.0.0 created successfully!
gh release view v2.0.0
