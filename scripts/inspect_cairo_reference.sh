#!/usr/bin/env bash
set -euo pipefail

# The user-supplied APK is YouTube Music 9.47.51 arm64-v8a, versionCode 94751240.
# APKPure's versionCode endpoint is used only to obtain a clean matching build for CI validation.
CLEAN_APK_URL="https://d.apkpure.net/b/APK/com.google.android.apps.youtube.music?nc=arm64-v8a&sv=32&versionCode=94751240"
CLEAN_APK="youtube-music-9.47.51-google-arm64.apk"
OUT="cairo-reference-report"
mkdir -p "$OUT"

echo "Downloading clean YouTube Music 9.47.51 ARM64 APK..."
curl --fail --location --retry 3 --retry-all-errors --output "$CLEAN_APK" "$CLEAN_APK_URL"
echo "Downloaded APK SHA-256:"
sha256sum "$CLEAN_APK"

BADGING="$(aapt dump badging "$CLEAN_APK")"
printf '%s\n' "$BADGING" | head -n 1
grep -Fq "package: name='com.google.android.apps.youtube.music'" <<< "$BADGING" || {
  echo "::error::Downloaded APK package does not match YouTube Music."
  exit 1
}
grep -Fq "versionName='9.47.51'" <<< "$BADGING" || {
  echo "::error::Downloaded APK version is not 9.47.51."
  exit 1
}

aapt dump resources "$CLEAN_APK" > "$OUT/RESOURCE_TABLE.txt"
grep -niE ':(drawable|mipmap|raw|layout|xml|id)/[^[:space:]]*(logo|icon|splash|launch|brand|header|music)|:string/[^[:space:]]*(app_name|app_launcher_name|youtube|music)' "$OUT/RESOURCE_TABLE.txt" | head -n 300 > "$OUT/RESOURCE_EVIDENCE.txt" || true

apktool d --force --no-res "$CLEAN_APK" --output "$OUT/clean"

{
  echo "ZUNOX Cairo splash inspection — YouTube Music 9.47.51"
  echo "Clean APK: $CLEAN_APK"
  echo "Clean APK SHA-256: $(sha256sum "$CLEAN_APK" | cut -d' ' -f1)"
  echo
  echo "Resource identity candidates:"
  cat "$OUT/RESOURCE_EVIDENCE.txt"
  echo
  echo "Manifest package/version:"
  grep -n -E 'package=|versionName|versionCode' "$OUT/clean/AndroidManifest.xml" || true
  echo
  echo "===== clean: MusicActivity onCreate fingerprint evidence ====="
  find "$OUT/clean" -type f -name 'MusicActivity.smali' -print
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    echo "--- $file ---"
    awk '
      /^\.method .*onCreate\(Landroid\/os\/Bundle;\)V/ { in_method=1; print; next }
      in_method && /^\.end method/ { print; in_method=0; next }
      in_method { print }
    ' "$file" | grep -n -E 'sa_e|setContentView|LottieAnimationView|main_activity_launch_animation|app_launch|inflate|findViewById' || true
  done < <(find "$OUT/clean" -type f -name 'MusicActivity.smali')
} > "$OUT/REPORT.txt"

python3 scripts/analyze_cairo_smali.py "$OUT" >> "$OUT/REPORT.txt"
python3 scripts/validate_cairo_patch_shape.py "$OUT/clean" >> "$OUT/REPORT.txt"

echo "Inspection report written to $OUT/REPORT.txt"

rm -rf "$OUT/clean"
rm -f "$CLEAN_APK"
