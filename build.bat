@echo off
setlocal enabledelayedexpansion

echo [1/6] Cleaning build outputs...
if not exist "build" mkdir build
if not exist "build\gen" mkdir build\gen
if not exist "build\classes" mkdir build\classes
if not exist "build\dex" mkdir build\dex

set "JAVA_BIN=C:\Users\sriji\AppData\Local\Programs\Microsoft\jdk-17.0.10.7-hotspot\bin"
set "AAPT2=tools\android-13\aapt2.exe"
set "ANDROID_JAR=tools\android-11\android.jar"
set "D8_JAR=tools\android-13\lib\d8.jar"
set "ZIPALIGN=tools\android-13\zipalign.exe"
set "APKSIGNER_JAR=tools\android-13\lib\apksigner.jar"

echo [2/6] Compiling resources with aapt2...
%AAPT2% compile --dir app\res -o build\res.zip
if %errorlevel% neq 0 (echo Failed aapt2 compile && exit /b 1)

echo [3/6] Linking APK and generating R.java...
%AAPT2% link -I %ANDROID_JAR% --manifest app\AndroidManifest.xml --java build\gen -o build\app-unaligned.apk build\res.zip --auto-add-overlay
if %errorlevel% neq 0 (echo Failed aapt2 link && exit /b 1)

echo [4/6] Compiling Java code...
"%JAVA_BIN%\javac.exe" -source 1.8 -target 1.8 -cp %ANDROID_JAR% -d build\classes build\gen\com\custom\pixellauncher\R.java app\src\com\custom\pixellauncher\*.java
if %errorlevel% neq 0 (echo Failed javac && exit /b 1)

echo [5/6] Dexing bytecode with D8...
"%JAVA_BIN%\java.exe" -cp %D8_JAR% com.android.tools.r8.D8 --release --lib %ANDROID_JAR% --output build\dex build\classes\com\custom\pixellauncher\*.class
if %errorlevel% neq 0 (echo Failed d8 && exit /b 1)

"%JAVA_BIN%\jar.exe" -uf build\app-unaligned.apk -C build\dex classes.dex
%ZIPALIGN% -f -p 4 build\app-unaligned.apk build\app-aligned.apk

echo [6/6] Signing APK...
if not exist "build\debug.keystore" (
    "%JAVA_BIN%\keytool.exe" -genkeypair -validity 10000 -dname CN=PixelLauncher -keystore build\debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048
)

"%JAVA_BIN%\java.exe" -jar %APKSIGNER_JAR% sign --ks build\debug.keystore --ks-pass pass:android --key-pass pass:android --out build\PixelLauncher.apk build\app-aligned.apk
if %errorlevel% neq 0 (echo Failed apksigner && exit /b 1)

echo.
echo Build Successful! APK generated at build\PixelLauncher.apk
