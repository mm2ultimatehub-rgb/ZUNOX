#!/usr/bin/env bash
set -euo pipefail

# Clean Google-signed 9.30.53 ARM64 APK (APKPure mirror).
CLEAN_APK_URL="https://d.apkpure.net/b/APK/com.google.android.apps.youtube.music?nc=arm64-v8a&sv=26&versionCode=93053240"
CLEAN_SHA256="1fe8d535ae445265c7250ed76a24c340e1dc89f2ab5d265f755800fe23ad334e"
CLEAN_APK="youtube-music-9.30.53-google-arm64.apk"

# Morphe-patched reference APK. Reference only; never use as a clean patch input.
MORPHE_APK_URL="https://github.com/znarfm/revanced-apks/releases/download/66/yt-music-morphe-v9.30.53-arm64-v8a.apk"
MORPHE_SHA256="e099ed21019240fd29234effa1dd16ad005d13605a31757699ff4d22254ee257"
MORPHE_APK="yt-music-morphe-v9.30.53-arm64-v8a.apk"

OUT="cairo-reference-report"
mkdir -p "$OUT"

echo "Downloading clean Google-signed YouTube Music 9.30.53 ARM64 APK..."
curl --fail --location --retry 3 --retry-all-errors --output "$CLEAN_APK" "$CLEAN_APK_URL"
echo "$CLEAN_SHA256  $CLEAN_APK" | sha256sum --check -
echo "Clean APK SHA-256 verified: $CLEAN_SHA256"

echo "Downloading Morphe-patched comparison APK (reference only)..."
curl --fail --location --retry 3 --retry-all-errors --output "$MORPHE_APK" "$MORPHE_APK_URL"
echo "$MORPHE_SHA256  $MORPHE_APK" | sha256sum --check -
echo "Morphe reference SHA-256 verified: $MORPHE_SHA256"

apktool d --force --no-res "$CLEAN_APK" --output "$OUT/clean"
apktool d --force --no-res "$MORPHE_APK" --output "$OUT/morphe-reference"

{
  echo "ZUNOX Cairo splash inspection — YouTube Music 9.30.53"
  echo "Clean APK: $CLEAN_APK"
  echo "Clean SHA-256: $CLEAN_SHA256"
  echo "Morphe comparison APK: $MORPHE_APK"
  echo "Morphe SHA-256: $MORPHE_SHA256"
  echo
  echo "IMPORTANT: only the first APK is the clean patch target. The Morphe APK is comparison material."
  echo
  echo "Manifest version/package:"
  grep -n -E 'package=|versionName|versionCode' "$OUT/clean/AndroidManifest.xml" || true
  echo
  for variant in clean morphe-reference; do
    echo "===== $variant: MusicActivity onCreate fingerprint evidence ====="
    find "$OUT/$variant" -type f -name 'MusicActivity.smali' -print
    while IFS= read -r file; do
      [ -n "$file" ] || continue
      echo "--- $file ---"
      awk '
        /^\.method .* onCreate\(Landroid\/os\/Bundle;\)V/ { in_method=1; print; next }
        in_method && /^\.end method/ { print; in_method=0; next }
        in_method { print }
      ' "$file" | grep -n -E 'sa_e|45635386|main_activity_launch_animation|setContentView|LottieAnimationView|app_launch|goto' || true
    done < <(find "$OUT/$variant" -type f -name 'MusicActivity.smali')
    echo
    echo "All related strings/resources:"
    grep -R -n -E 'main_activity_launch_animation|app_launch|sa_e|45635386' "$OUT/$variant/res" "$OUT/$variant/smali"* 2>/dev/null | head -n 120 || true
    echo
  done
} > "$OUT/REPORT.txt"

python3 scripts/analyze_cairo_smali.py "$OUT" >> "$OUT/REPORT.txt"
python3 scripts/validate_cairo_patch_shape.py "$OUT/clean" >> "$OUT/REPORT.txt"

echo "Inspection report written to $OUT/REPORT.txt"

# Keep only the small, reviewable text reports in the uploaded artifact.
rm -rf "$OUT/clean" "$OUT/morphe-reference"
rm -f "$CLEAN_APK" "$MORPHE_APK"
