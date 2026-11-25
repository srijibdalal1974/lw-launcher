@echo off
setlocal
cd /d "%~dp0"
python build_release.py
if %errorlevel% neq 0 (
    echo.
    echo Release build failed!
    exit /b %errorlevel%
)
