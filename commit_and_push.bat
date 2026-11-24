@echo off
set "PATH=C:\Users\sriji\Downloads\mingit\cmd;C:\Users\sriji\Downloads\gh;%PATH%"
cd /d C:\Users\sriji\.gemini\antigravity\scratch\pixel_launcher_custom

git add -A
git commit -m "Remove custom popup navigation and recents overlay for clean native system compatibility"
git push origin main
