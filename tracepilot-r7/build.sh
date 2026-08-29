#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[ -n "$SDK" ] || { echo 'ANDROID_SDK_ROOT missing' >&2; exit 2; }
BT="$SDK/build-tools/35.0.1"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$ANDROID_JAR"; do [ -e "$f" ] || { echo "Missing $f" >&2; exit 3; }; done
rm -rf "$ROOT/build" "$ROOT/dist"
mkdir -p "$ROOT/build/gen" "$ROOT/build/classes" "$ROOT/build/dex" "$ROOT/dist"
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$ROOT/build/resources.zip"
"$BT/aapt2" link -o "$ROOT/build/base.apk" -I "$ANDROID_JAR" --manifest "$ROOT/app/src/main/AndroidManifest.xml" --java "$ROOT/build/gen" --min-sdk-version 26 --target-sdk-version 28 "$ROOT/build/resources.zip"
find "$ROOT/app/src/main/java" "$ROOT/build/gen" -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$ROOT/build/classes"
(cd "$ROOT/build/classes" && jar cf "$ROOT/build/classes.jar" .)
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$ROOT/build/dex" "$ROOT/build/classes.jar"
cp "$ROOT/build/base.apk" "$ROOT/build/unsigned-unaligned.apk"
(cd "$ROOT/build/dex" && zip -q -j "$ROOT/build/unsigned-unaligned.apk" classes*.dex)
"$BT/zipalign" -f -p 4 "$ROOT/build/unsigned-unaligned.apk" "$ROOT/dist/TracePilot-R7-FULLCORE-UNSIGNED.apk"
unzip -t "$ROOT/dist/TracePilot-R7-FULLCORE-UNSIGNED.apk"
"$BT/aapt2" dump badging "$ROOT/dist/TracePilot-R7-FULLCORE-UNSIGNED.apk" | head -30
sha256sum "$ROOT/dist/TracePilot-R7-FULLCORE-UNSIGNED.apk" | tee "$ROOT/dist/TracePilot-R7-FULLCORE-UNSIGNED.apk.sha256"
(cd "$ROOT" && zip -qr "$ROOT/dist/TracePilot-R7-source.zip" app build.sh README.md -x '*/build/*' '*/dist/*')
echo BUILD_OK
