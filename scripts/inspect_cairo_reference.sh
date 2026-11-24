#!/usr/bin/env bash
set -euo pipefail

APK_URL="https://github.com/znarfm/revanced-apks/releases/download/66/yt-music-morphe-v9.30.53-arm64-v8a.apk"
EXPECTED_SHA256="e099ed21019240fd29234effa1dd16ad005d13605a31757699ff4d22254ee257"
APK="yt-music-morphe-v9.30.53-arm64-v8a.apk"
OUT="cairo-reference-report"

mkdir -p "$OUT"
echo "Downloading reference APK (Morphe-patched; NOT a clean Google base APK)." | tee "$OUT/README.txt"
curl --fail --location --retry 3 --output "$APK" "$APK_URL"

echo "$EXPECTED_SHA256  $APK" | sha256sum --check -
echo "SHA-256 verified: $EXPECTED_SHA256" | tee -a "$OUT/README.txt"

apktool d --force "$APK" --output "$OUT/decoded"

{
  echo "ZUNOX Cairo reference inspection"
  echo "APK: $APK"
  echo "Expected SHA-256: $EXPECTED_SHA256"
  echo "Important: this is a prepatched Morphe APK and is reference-only."
  echo
  echo "Relevant manifest/resource metadata:"
  grep -R -n -E 'package=|versionName|versionCode|main_activity_launch_animation|app_launch' "$OUT/decoded/AndroidManifest.xml" "$OUT/decoded/res" 2>/dev/null | head -n 160 || true
  echo
  echo "Relevant DEX/smali references:"
  grep -R -n -E 'disableCairoSplashAnimation|CairoSplashAnimation|sa_e|main_activity_launch_animation|MusicActivity' "$OUT/decoded/smali"* 2>/dev/null | head -n 300 || true
} > "$OUT/REPORT.txt"

echo "Inspection report written to $OUT/REPORT.txt"
