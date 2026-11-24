# ZUNOX Cairo splash patch — YouTube Music 9.30.53

## Research status

- Target package: `com.google.android.apps.youtube.music`
- Target version: `9.30.53` (Android min SDK 26).
- The current Morphe patch catalog explicitly lists 9.30.53 as a supported YouTube Music target.
- Morphe's current implementation handles the startup Lottie view from `MusicActivity.onCreate`, identified by the `main_activity_launch_animation` layout resource, the `LottieAnimationView` cast, and the `app_launch` raw resource.
- The current Morphe implementation exposes a disabled splash style by returning a null Lottie view. It also has a separate startup-content-ready hook. That complete implementation depends on Morphe extension classes and cannot be copied as a standalone patch into ZUNOX.
- The current Morphe-derived fingerprint narrows the match to `Lcom/google/android/apps/youtube/music/activities/MusicActivity;->onCreate(Landroid/os/Bundle;)V`, requires the `sa_e` string, and additionally checks for a `setContentView` call plus either literal `45635386` or the `main_activity_launch_animation` resource ID. This is a useful implementation reference, but it is not proof that the exact fingerprint matches every 9.30.53 APK build.
- ZUNOX loads external patch JARs through ReVanced Patcher Android `22.0.2-dev.1`. Morphe Patcher is a different implementation/namespace and is not a drop-in dependency.

## Reference APK now wired into GitHub Actions

A reproducible inspection path has been added to this branch:

- `scripts/inspect_cairo_reference.sh` downloads and SHA-256-verifies two APKs, then decodes both with APKTool.
- Clean Google-signed 9.30.53 ARM64 APK: APKPure download endpoint, versionCode `93053240`, SHA-256 `1fe8d535ae445265c7250ed76a24c340e1dc89f2ab5d265f755800fe23ad334e`.
- Morphe-patched comparison APK: `yt-music-morphe-v9.30.53-arm64-v8a.apk`, SHA-256 `e099ed21019240fd29234effa1dd16ad005d13605a31757699ff4d22254ee257`.
- `.github/workflows/inspect_cairo_reference.yml` runs APKTool and uploads both decoded folders and a focused `REPORT.txt` as the `cairo-reference-inspection` GitHub Actions artifact.
- The report extracts `MusicActivity.onCreate(Bundle)` evidence from the clean APK and the Morphe comparison, including `sa_e`, `setContentView`, `main_activity_launch_animation`, and Cairo flag references.
- APKMirror lists the same Google-signed build as version `9.30.53` / `93053240`, arm64-v8a, nodpi, Android 8.0+, and provides the Google signing certificate fingerprint. The exact APKPure file hash is pinned above.
- Upstream Morphe comparison release: `https://github.com/znarfm/revanced-apks/releases/tag/66`.

**Important:** the Morphe asset is already patched and is comparison material only. The clean APK download is the intended fingerprint-validation input; the workflow must pass both SHA checks before decoding either file.

## Implementation direction

Build a small ZUNOX-native patch bundle against the exact ReVanced Patcher version used by the app. Avoid migrating the manager to Morphe Patcher.

Prefer a minimal in-place edit of the target startup layout/view that suppresses only the Cairo Lottie view while leaving MusicActivity initialization and the rest of the launch flow intact. Do not early-return from Activity.onCreate. Do not add a Morphe runtime dependency.

## Required verification before release

1. Run the new `Inspect YouTube Music 9.30.53 Cairo reference` workflow and inspect its artifact.
2. Obtain a clean 9.30.53 APK and verify the fingerprint against its DEX; do not use the Morphe-patched reference as a clean base.
3. Port the fingerprint and bytecode edit to ReVanced Patcher 22.0.2-dev.1 without importing Morphe-only APIs.
4. Compile the patch bundle against ReVanced Patcher 22.0.2-dev.1.
5. Apply the patch to a clean 9.30.53 APK and verify the app starts, loads its home screen, and plays audio.
6. Confirm the Cairo animation is absent and no blank overlay or startup hang remains.
7. Run ZUNOX's Android release build and publish an APK only after the build succeeds.

## Safety boundary

This is a research/automation improvement, not a claim that the Cairo patch has been implemented or tested. No ZUNOX APK is produced by this document or the reference-inspection workflow.
