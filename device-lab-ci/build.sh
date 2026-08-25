#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
BT="$SDK/build-tools/35.0.1"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$ANDROID_JAR"; do
  [ -e "$f" ] || { echo "Missing Android build component: $f" >&2; exit 1; }
done
rm -rf "$ROOT/build" "$ROOT/dist"
mkdir -p "$ROOT/build/gen" "$ROOT/build/classes" "$ROOT/build/dex" "$ROOT/dist"
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$ROOT/build/resources.zip"
"$BT/aapt2" link \
  -o "$ROOT/build/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --java "$ROOT/build/gen" \
  --min-sdk-version 26 --target-sdk-version 35 \
  "$ROOT/build/resources.zip"
find "$ROOT/app/src/main/java" "$ROOT/build/gen" -name '*.java' -print0 | \
  xargs -0 javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$ROOT/build/classes"
(cd "$ROOT/build/classes" && jar cf "$ROOT/build/classes.jar" .)
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$ROOT/build/dex" "$ROOT/build/classes.jar"
cp "$ROOT/build/base.apk" "$ROOT/build/unsigned.apk"
(cd "$ROOT/build/dex" && zip -q -j "$ROOT/build/unsigned.apk" classes*.dex)
"$BT/zipalign" -f -p 4 "$ROOT/build/unsigned.apk" "$ROOT/build/aligned.apk"
keytool -genkeypair -noprompt -keystore "$ROOT/build/devicelab-dev.jks" -storepass android -keypass android \
  -alias devicelab -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=Device Lab Development, OU=Device Lab, O=Device Lab, L=Martigues, C=FR"
"$BT/apksigner" sign \
  --ks "$ROOT/build/devicelab-dev.jks" --ks-key-alias devicelab \
  --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/dist/DeviceLab-v4.0.apk" "$ROOT/build/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$ROOT/dist/DeviceLab-v4.0.apk"
"$BT/aapt2" dump badging "$ROOT/dist/DeviceLab-v4.0.apk" | head -25
sha256sum "$ROOT/dist/DeviceLab-v4.0.apk" | tee "$ROOT/dist/DeviceLab-v4.0.apk.sha256"
unzip -t "$ROOT/dist/DeviceLab-v4.0.apk"
echo "APK=$ROOT/dist/DeviceLab-v4.0.apk"
