@echo off
set "PATH=C:\Users\sriji\Downloads\mingit\cmd;C:\Users\sriji\Downloads\gh;%PATH%"
cd /d C:\Users\sriji\.gemini\antigravity\scratch\pixel_launcher_custom

echo Committing any pending changes...
git add -A
git commit -m "LW Launcher v1.0.3: TargetSDK 36, .aab App Bundle release, and release signing keystore"
git push origin main

echo Creating GitHub Release v1.0.3...
gh release create v1.0.3 "release\LW_Launcher.aab" "release\LW_Launcher.apk" --title "LW Launcher v1.0.3 - Google Play Bundle (.AAB) and TargetSDK 36" --notes-file "release_notes_v103.md"
if %errorlevel% neq 0 (
    echo Failed to create release
    exit /b %errorlevel%
)

echo Release created successfully!
gh release view v1.0.3
