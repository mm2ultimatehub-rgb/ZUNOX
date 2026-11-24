# ZUNOX Cairo splash patch — YouTube Music 9.30.53

## Research status

- Target package: `com.google.android.apps.youtube.music`
- Target version: `9.30.53` (Android min SDK 26).
- The current Morphe patch catalog explicitly lists 9.30.53 as a supported YouTube Music target.
- Morphe's current implementation handles the startup Lottie view from `MusicActivity.onCreate`, identified by the `main_activity_launch_animation` layout resource, the `LottieAnimationView` cast, and the `app_launch` raw resource.
- The current Morphe implementation exposes a disabled splash style by returning a null Lottie view. It also has a separate startup-content-ready hook. That complete implementation depends on Morphe extension classes and cannot be copied as a standalone patch into ZUNOX.
- The current Morphe-derived fingerprint narrows the match to `Lcom/google/android/apps/youtube/music/activities/MusicActivity;->onCreate(Landroid/os/Bundle;)V`, requires the `sa_e` string, and additionally checks for a `setContentView` call plus either literal `45635386` or the `main_activity_launch_animation` resource ID. This is a useful implementation reference, but it is not proof that the exact fingerprint matches every 9.30.53 APK build.
- ZUNOX loads external patch JARs through ReVanced Patcher Android `22.0.2-dev.1`. Morphe Patcher is a different implementation/namespace and is not a drop-in dependency.

## Implementation direction

Build a small ZUNOX-native patch bundle against the exact ReVanced Patcher version used by the app. Avoid migrating the manager to Morphe Patcher.

Prefer a minimal in-place edit of the target startup layout/view that suppresses only the Cairo Lottie view while leaving MusicActivity initialization and the rest of the launch flow intact. Do not early-return from Activity.onCreate. Do not add a Morphe runtime dependency.

## Required verification before release

1. Port the fingerprint and bytecode edit to ReVanced Patcher 22.0.2-dev.1 without importing Morphe-only APIs.
2. Compile the patch bundle against ReVanced Patcher 22.0.2-dev.1.
3. Confirm the fingerprint matches the actual YouTube Music 9.30.53 APK, not just a version string.
4. Apply the patch to a clean 9.30.53 APK and verify the app starts, loads its home screen, and plays audio.
5. Confirm the Cairo animation is absent and no blank overlay or startup hang remains.
6. Run ZUNOX's Android release build and publish an APK only after the build succeeds.

## Safety boundary

This is a research/implementation note, not a claim that the patch has been implemented or tested. No APK is produced by this document.
