@echo off
set "PATH=C:\Users\sriji\Downloads\mingit\cmd;C:\Users\sriji\Downloads\gh;%PATH%"
cd /d C:\Users\sriji\.gemini\antigravity\scratch\pixel_launcher_custom

git add -A
git commit -m "Fix 3-button navigation bar overlap with dynamic WindowInsets padding"
git push origin main
gh release upload v1.0.1 release\LW_Launcher.apk --clobber --repo srijibdalal1974/lw-launcher
