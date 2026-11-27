#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
DIST="$ROOT/dist"
APK_NAME="TV-Auto-Clean-v1.2.1.apk"

# JDK 17 is required. Set JAVA_HOME explicitly if auto-detection fails.
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -x /usr/libexec/java_home ]; then
        JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    fi
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
    echo "JDK 17 not found. Set JAVA_HOME." >&2
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

# Android SDK: set ANDROID_SDK_ROOT, or use the default macOS/Linux path.
SDK="${ANDROID_SDK_ROOT:-}"
if [ -z "$SDK" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        SDK="$HOME/Library/Android/sdk"
    else
        SDK="$HOME/Android/Sdk"
    fi
fi
BUILD_TOOLS="$SDK/build-tools/33.0.2"
PLATFORM="$SDK/platforms/android-33/android.jar"
if [ ! -f "$PLATFORM" ] || [ ! -x "$BUILD_TOOLS/aapt2" ]; then
    echo "Android SDK 33 / build-tools 33.0.2 not found under $SDK" >&2
    echo "Install: sdkmanager 'platforms;android-33' 'build-tools;33.0.2'" >&2
    exit 1
fi

# Never commit the keystore. See scripts/create-keystore.sh and README.
KEYSTORE="${TV_CLEAN_KEYSTORE:-$ROOT/keystore/release.keystore}"
KS_ALIAS="${TV_CLEAN_KEY_ALIAS:-tvclean}"
if [ ! -f "$KEYSTORE" ]; then
    echo "Signing keystore not found: $KEYSTORE" >&2
    echo "Run scripts/create-keystore.sh, or set TV_CLEAN_KEYSTORE." >&2
    exit 1
fi
if [ -z "${TV_CLEAN_STORE_PASS:-}" ]; then
    echo "TV_CLEAN_STORE_PASS is not set." >&2
    echo "Export the keystore password, then re-run." >&2
    exit 1
fi
export TV_CLEAN_KEY_PASS="${TV_CLEAN_KEY_PASS:-$TV_CLEAN_STORE_PASS}"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$DIST"

echo "[1/7] Compiling resources"
"$BUILD_TOOLS/aapt2" compile --dir "$ROOT/res" -o "$BUILD/res.zip"

echo "[2/7] Linking resources"
"$BUILD_TOOLS/aapt2" link \
    -o "$BUILD/unsigned.apk" \
    -I "$PLATFORM" \
    --manifest "$ROOT/AndroidManifest.xml" \
    -R "$BUILD/res.zip" \
    --java "$BUILD/gen" \
    --auto-add-overlay \
    --min-sdk-version 21 \
    --target-sdk-version 33 \
    --version-code 4 \
    --version-name 1.2.1

echo "[3/7] Compiling Java"
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
    -bootclasspath "$PLATFORM" \
    -d "$BUILD/classes" \
    "$BUILD/gen/com/tvautoclean/guard/R.java" \
    "$ROOT"/src/com/tvautoclean/guard/*.java

echo "[4/7] Dexing"
"$BUILD_TOOLS/d8" --release --min-api 21 --lib "$PLATFORM" \
    --output "$BUILD/dex" \
    "$BUILD"/classes/com/tvautoclean/guard/*.class

echo "[5/7] Packaging"
( cd "$BUILD/dex" && zip -q -u "$BUILD/unsigned.apk" classes.dex )
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

echo "[6/7] Signing"
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KS_ALIAS" \
    --ks-pass env:TV_CLEAN_STORE_PASS \
    --key-pass env:TV_CLEAN_KEY_PASS \
    --out "$ROOT/$APK_NAME" \
    "$BUILD/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$ROOT/$APK_NAME"

echo "[7/7] Inspecting"
"$BUILD_TOOLS/aapt2" dump badging "$ROOT/$APK_NAME" | head -25
cp "$ROOT/$APK_NAME" "$DIST/$APK_NAME"
ls -lh "$ROOT/$APK_NAME"
echo "Done: $ROOT/$APK_NAME"
