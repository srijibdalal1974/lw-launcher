import os
import sys
import shutil
import zipfile
import subprocess
import hashlib

WORKSPACE = os.path.abspath(os.path.dirname(__file__))
JAVA_BIN = r"C:\Users\sriji\AppData\Local\Programs\Microsoft\jdk-17.0.10.7-hotspot\bin"
AAPT2 = os.path.join(WORKSPACE, "tools", "android-13", "aapt2.exe")
ANDROID_JAR = os.path.join(WORKSPACE, "tools", "android-11", "android.jar")
D8_JAR = os.path.join(WORKSPACE, "tools", "android-13", "lib", "d8.jar")
ZIPALIGN = os.path.join(WORKSPACE, "tools", "android-13", "zipalign.exe")
APKSIGNER_JAR = os.path.join(WORKSPACE, "tools", "android-13", "lib", "apksigner.jar")
BUNDLETOOL_JAR = os.path.join(WORKSPACE, "tools", "bundletool.jar")

KEYTOOL = os.path.join(JAVA_BIN, "keytool.exe")
JAVAC = os.path.join(JAVA_BIN, "javac.exe")
JAVA = os.path.join(JAVA_BIN, "java.exe")
JARSIGNER = os.path.join(JAVA_BIN, "jarsigner.exe")
JAR = os.path.join(JAVA_BIN, "jar.exe")

KEYSTORE_PATH = os.path.join(WORKSPACE, "release", "release.keystore")
KEYSTORE_PASS = "lwlauncher_release_key_2026"
KEYSTORE_ALIAS = "lwlauncher"

def run_cmd(cmd, desc):
    print(f"--> {desc}...")
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=WORKSPACE)
    if result.returncode != 0:
        print(f"ERROR: {desc} failed (exit code {result.returncode})")
        if result.stdout:
            print("STDOUT:", result.stdout)
        if result.stderr:
            print("STDERR:", result.stderr)
        sys.exit(1)
    return result.stdout

def get_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()

def main():
    print("=" * 60)
    print("  LW Launcher v4.0.1 - Production Release Build Pipeline")
    print("  Outputting Signed .AAB (Google Play) & Signed .APK")
    print("=" * 60)

    # 1. Prepare directories
    build_dir = os.path.join(WORKSPACE, "build")
    release_dir = os.path.join(WORKSPACE, "release")
    gen_dir = os.path.join(build_dir, "gen")
    classes_dir = os.path.join(build_dir, "classes")
    dex_dir = os.path.join(build_dir, "dex")

    for d in [build_dir, release_dir, gen_dir, classes_dir, dex_dir]:
        os.makedirs(d, exist_ok=True)

    # 2. Ensure release keystore exists
    if not os.path.exists(KEYSTORE_PATH):
        print("[1/8] Generating Dedicated Release Keystore...")
        cmd_gen_key = (
            f'"{KEYTOOL}" -genkeypair -validity 10000 '
            f'-dname "CN=LW Launcher, OU=Release, O=LW Launcher Org, C=IN" '
            f'-keystore "{KEYSTORE_PATH}" -storepass {KEYSTORE_PASS} -keypass {KEYSTORE_PASS} '
            f'-alias {KEYSTORE_ALIAS} -keyalg RSA -keysize 2048'
        )
        run_cmd(cmd_gen_key, "Generate Release Keystore")
    else:
        print(f"[1/8] Reusing existing Release Keystore at {KEYSTORE_PATH}")

    # 3. Compile resources
    print("[2/8] Compiling resources with aapt2...")
    res_zip = os.path.join(build_dir, "res.zip")
    run_cmd(f'"{AAPT2}" compile --dir app\\res -o "{res_zip}"', "Compile Resources")

    # 4. Link proto-format APK for AAB & generate R.java
    print("[3/8] Linking proto-format APK for Android App Bundle (.aab)...")
    proto_apk = os.path.join(build_dir, "proto.apk")
    run_cmd(
        f'"{AAPT2}" link -I "{ANDROID_JAR}" --manifest app\\AndroidManifest.xml '
        f'-o "{proto_apk}" "{res_zip}" --auto-add-overlay --proto-format',
        "Link Proto APK"
    )

    print("[4/8] Linking binary-format APK and generating R.java for universal APK...")
    unaligned_apk = os.path.join(build_dir, "app-unaligned.apk")
    run_cmd(
        f'"{AAPT2}" link -I "{ANDROID_JAR}" --manifest app\\AndroidManifest.xml '
        f'--java "{gen_dir}" -o "{unaligned_apk}" "{res_zip}" --auto-add-overlay',
        "Link Binary APK and generate R.java"
    )

    # 5. Compile Java sources
    print("[5/8] Compiling Java source files with javac...")
    r_java = os.path.join(gen_dir, "com", "custom", "pixellauncher", "R.java")
    java_sources = os.path.join(WORKSPACE, "app", "src", "com", "custom", "pixellauncher", "*.java")
    run_cmd(
        f'"{JAVAC}" -source 1.8 -target 1.8 -cp "{ANDROID_JAR}" -d "{classes_dir}" "{r_java}" {java_sources}',
        "Compile Java Sources"
    )

    # 6. Dex bytecode with D8
    print("[6/8] Dexing bytecode with D8...")
    class_files = os.path.join(classes_dir, "com", "custom", "pixellauncher", "*.class")
    run_cmd(
        f'"{JAVA}" -cp "{D8_JAR}" com.android.tools.r8.D8 --release --lib "{ANDROID_JAR}" '
        f'--output "{dex_dir}" {class_files}',
        "Dex with D8"
    )

    # 7. Assemble Base Module Zip for AAB
    print("[7/8] Assembling base module and building Android App Bundle (.aab)...")
    base_zip_path = os.path.join(build_dir, "base.zip")
    classes_dex_path = os.path.join(dex_dir, "classes.dex")

    with zipfile.ZipFile(base_zip_path, "w", compression=zipfile.ZIP_DEFLATED) as base_zip:
        with zipfile.ZipFile(proto_apk, "r") as proto_zip:
            for item in proto_zip.infolist():
                data = proto_zip.read(item.filename)
                if item.filename == "AndroidManifest.xml":
                    base_zip.writestr("manifest/AndroidManifest.xml", data)
                elif item.filename == "resources.pb":
                    base_zip.writestr("resources.pb", data)
                elif item.filename.startswith("res/"):
                    base_zip.writestr(item.filename, data)
                else:
                    base_zip.writestr(item.filename, data)
        # Add dex
        base_zip.write(classes_dex_path, "dex/classes.dex")

    # Build AAB with bundletool
    aab_path = os.path.join(release_dir, "LW_Launcher.aab")
    if os.path.exists(aab_path):
        os.remove(aab_path)

    run_cmd(
        f'"{JAVA}" -jar "{BUNDLETOOL_JAR}" build-bundle --modules="{base_zip_path}" --output="{aab_path}"',
        "Build Bundle with bundletool"
    )

    # Sign AAB with jarsigner
    run_cmd(
        f'"{JARSIGNER}" -keystore "{KEYSTORE_PATH}" -storepass {KEYSTORE_PASS} -keypass {KEYSTORE_PASS} '
        f'"{aab_path}" {KEYSTORE_ALIAS}',
        "Sign .AAB with jarsigner"
    )

    # Validate AAB with bundletool
    run_cmd(
        f'"{JAVA}" -jar "{BUNDLETOOL_JAR}" validate --bundle="{aab_path}"',
        "Validate .AAB"
    )

    # 8. Build Universal Signed APK for Direct Installation
    print("[8/8] Packaging, zipaligning and signing universal APK...")
    # Add classes.dex to binary unaligned apk
    with zipfile.ZipFile(unaligned_apk, "a") as apk_zip:
        apk_zip.write(classes_dex_path, "classes.dex")

    aligned_apk = os.path.join(build_dir, "app-aligned.apk")
    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)

    run_cmd(f'"{ZIPALIGN}" -f -p 4 "{unaligned_apk}" "{aligned_apk}"', "Zipalign APK")

    apk_path = os.path.join(release_dir, "LW_Launcher.apk")
    if os.path.exists(apk_path):
        os.remove(apk_path)

    run_cmd(
        f'"{JAVA}" -jar "{APKSIGNER_JAR}" sign --ks "{KEYSTORE_PATH}" '
        f'--ks-pass pass:{KEYSTORE_PASS} --key-pass pass:{KEYSTORE_PASS} '
        f'--out "{apk_path}" "{aligned_apk}"',
        "Sign APK with apksigner"
    )

    # Verify APK with apksigner
    run_cmd(
        f'"{JAVA}" -jar "{APKSIGNER_JAR}" verify --verbose "{apk_path}"',
        "Verify Signed APK"
    )

    # Produce versioned assets
    aab_v400_path = os.path.join(release_dir, "LW_Launcher_v4.0.1.aab")
    apk_v400_path = os.path.join(release_dir, "LW_Launcher_v4.0.1.apk")
    shutil.copy2(aab_path, aab_v400_path)
    shutil.copy2(apk_path, apk_v400_path)

    # Deliver to Downloads directories
    download_dirs = [
        r"C:\Users\sriji\Downloads\LW_Launcher",
        r"C:\Users\sriji\Downloads\lw laucher v4.0.1",
    ]
    for d in download_dirs:
        os.makedirs(d, exist_ok=True)
        shutil.copy2(aab_path, os.path.join(d, "LW_Launcher.aab"))
        shutil.copy2(apk_path, os.path.join(d, "LW_Launcher.apk"))
        shutil.copy2(aab_v400_path, os.path.join(d, "LW_Launcher_v4.0.1.aab"))
        shutil.copy2(apk_v400_path, os.path.join(d, "LW_Launcher_v4.0.1.apk"))
        print(f"[OK] Delivered v4.0.1 release assets to {d}")

    # Summary
    aab_size = os.path.getsize(aab_path)
    apk_size = os.path.getsize(apk_path)
    aab_sha = get_sha256(aab_path)
    apk_sha = get_sha256(apk_path)

    print("\n" + "=" * 60)
    print("  BUILD SUCCESSFUL!")
    print("=" * 60)
    print(f"Android App Bundle (.aab):")
    print(f"  Path:   {aab_path}")
    print(f"  Size:   {aab_size:,} bytes ({aab_size / 1024:.1f} KB)")
    print(f"  SHA256: {aab_sha}")
    print()
    print(f"Universal Test APK (.apk):")
    print(f"  Path:   {apk_path}")
    print(f"  Size:   {apk_size:,} bytes ({apk_size / 1024:.1f} KB)")
    print(f"  SHA256: {apk_sha}")
    print()
    print(f"Release Keystore:")
    print(f"  Path:   {KEYSTORE_PATH}")
    print(f"  Alias:  {KEYSTORE_ALIAS}")
    print("=" * 60)

if __name__ == "__main__":
    main()
