@echo off
set "GIT=C:\Users\sriji\Downloads\mingit\cmd\git.exe"
cd /d C:\Users\sriji\.gemini\antigravity\scratch\pixel_launcher_custom
%GIT% config user.name "srijibdalal1974"
%GIT% config user.email "srijibdalal1974@users.noreply.github.com"
%GIT% add .
%GIT% commit -m "LW Launcher: Ultra-lightweight Android launcher with Android 17 recents, gesture navigation and 60fps DragLayer"
%GIT% branch -M main
%GIT% log -1
