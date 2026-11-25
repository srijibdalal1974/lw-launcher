import os
import shutil
import subprocess
import time

ADB = r"C:\Users\sriji\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
GIT = r"C:\Users\sriji\Downloads\mingit\cmd\git.exe"
GH = r"C:\Users\sriji\Downloads\gh\gh.exe"
WORKSPACE = r"C:\Users\sriji\.gemini\antigravity\scratch\pixel_launcher_custom"
DOWNLOADS_DEST = r"C:\Users\sriji\Downloads\LW_Launcher"
ARTIFACT_DIR = r"C:\Users\sriji\.gemini\antigravity\brain\266f2d09-f40d-4999-b2d5-11a99397f83b"

def run(cmd, desc, cwd=None, ignore_err=False):
    print(f"--> {desc}...")
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=cwd or WORKSPACE)
    if res.returncode != 0 and not ignore_err:
        print(f"FAILED: {desc}")
        print("STDOUT:", res.stdout)
        print("STDERR:", res.stderr)
        return False, res.stdout, res.stderr
    return True, res.stdout, res.stderr

def main():
    # 1. Copy to Downloads folder
    os.makedirs(DOWNLOADS_DEST, exist_ok=True)
    aab_src = os.path.join(WORKSPACE, "release", "LW_Launcher.aab")
    apk_src = os.path.join(WORKSPACE, "release", "LW_Launcher.apk")
    
    aab_dst = os.path.join(DOWNLOADS_DEST, "LW_Launcher.aab")
    apk_dst = os.path.join(DOWNLOADS_DEST, "LW_Launcher.apk")
    
    shutil.copy2(aab_src, aab_dst)
    shutil.copy2(apk_src, apk_dst)
    print(f"[OK] Saved .aab and .apk to {DOWNLOADS_DEST}")

    # 2. Install on phone
    ok, out, err = run(f'"{ADB}" install -r "{apk_src}"', "Install APK on phone via ADB")
    if ok:
        print(f"[OK] ADB install output: {out.strip()}")
    else:
        print(f"[WARN] ADB install failed or device not connected: {err.strip()}")

    # 3. Launch MainActivity
    run(f'"{ADB}" shell am start -n com.custom.pixellauncher/.MainActivity', "Launch LW Launcher on phone", ignore_err=True)
    time.sleep(2)

    # 4. Capture screenshot
    screenshot_path = os.path.join(ARTIFACT_DIR, "v103_play_ready_test.png")
    run(f'"{ADB}" exec-out screencap -p > "{screenshot_path}"', "Take screenshot of launcher", ignore_err=True)
    if os.path.exists(screenshot_path) and os.path.getsize(screenshot_path) > 1000:
        print(f"[OK] Screenshot captured at {screenshot_path}")

    # 5. Git status & commit
    env = os.environ.copy()
    env["PATH"] = rf"C:\Users\sriji\Downloads\mingit\cmd;C:\Users\sriji\Downloads\gh;{env.get('PATH', '')}"

    run(f'"{GIT}" add -A', "Git add all changes")
    commit_msg = (
        "Prepare build for Google Play: targetSdk 36 (Android 16), .aab App Bundle pipeline, "
        "dedicated release keystore signing, and RoleManager home prompt"
    )
    run(f'"{GIT}" commit -m "{commit_msg}"', "Git commit", ignore_err=True)
    run(f'"{GIT}" push origin main', "Push to GitHub", ignore_err=True)

    # 6. Create GitHub Release v1.0.3
    release_notes = (
        "## LW Launcher v1.0.3 — Google Play Ready (.AAB & Android 16 TargetSDK)\n\n"
        "### 📦 Deliverables & Updates:\n"
        "- **Android App Bundle (.aab)**: Full Google Play-compliant bundle built with `bundletool` and proto-format AAPT2.\n"
        "- **Updated Target SDK**: Upgraded `targetSdkVersion` to **36** (Android 16) with `minSdkVersion=26`.\n"
        "- **Dedicated Release Keystore**: Release signing with 2048-bit RSA release keystore (`release.keystore`).\n"
        "- **Default Home Role Prompt**: Modern `RoleManager` (API 29+) integration with graceful fallback for seamless system home assignment.\n"
        "- **Dual Release Assets**: Includes both `.aab` for Google Play Console upload and `.apk` for direct offline installation.\n"
    )
    notes_file = os.path.join(WORKSPACE, "release_notes_v103.md")
    with open(notes_file, "w", encoding="utf-8") as f:
        f.write(release_notes)

    create_rel_cmd = (
        f'"{GH}" release create v1.0.3 '
        f'"{aab_src}" "{apk_src}" '
        f'--title "LW Launcher v1.0.3 - Google Play Bundle (.AAB) & TargetSDK 36" '
        f'--notes-file "{notes_file}"'
    )
    ok, out, err = run(create_rel_cmd, "Create GitHub Release v1.0.3", ignore_err=True)
    if ok:
        print(f"[OK] Release v1.0.3 created on GitHub! {out.strip()}")
    else:
        print(f"[NOTE] GitHub Release creation: {err.strip()} / {out.strip()}")

if __name__ == "__main__":
    main()
