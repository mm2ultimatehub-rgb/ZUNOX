# ZUNOX Cairo splash patch — YouTube Music 9.30.53

## Research status

- Target package: `com.google.android.apps.youtube.music`
- Target version: `9.30.53` (Android min SDK 26).
- The current Morphe patch catalog explicitly lists 9.30.53 as a supported YouTube Music target.
- Morphe's current implementation handles the startup Lottie view from `MusicActivity.onCreate`, identified by the `main_activity_launch_animation` layout resource, the `LottieAnimationView` cast, and the `app_launch` raw resource.
- The current Morphe implementation exposes a disabled splash style by returning a null Lottie view. It also has a separate startup-content-ready hook. That complete implementation depends on Morphe extension classes and cannot be copied as a standalone patch into ZUNOX.
- Older ReVanced-derived Cairo patches use the `sa_e` string and direct bytecode edits, but their published compatibility ranges stop at older YouTube Music versions and must not be assumed to work on 9.30.53.
- ZUNOX loads external patch JARs through ReVanced Patcher Android `22.0.2-dev.1`. Morphe Patcher is a different implementation/namespace and is not a drop-in dependency.

## Implementation direction

Build a small ZUNOX-native patch bundle against the exact ReVanced Patcher version used by the app. Avoid migrating the manager to Morphe Patcher.

Prefer a minimal in-place edit of the target startup layout/view that suppresses only the Cairo Lottie view while leaving MusicActivity initialization and the rest of the launch flow intact. Do not early-return from Activity.onCreate. Do not add a Morphe runtime dependency.

## Required verification before release

1. Compile the patch bundle against ReVanced Patcher 22.0.2-dev.1.
2. Confirm the fingerprint matches the actual YouTube Music 9.30.53 APK, not just a version string.
3. Apply the patch to a clean 9.30.53 APK and verify the app starts, loads its home screen, and plays audio.
4. Confirm the Cairo animation is absent and no blank overlay or startup hang remains.
5. Run ZUNOX's Android release build and publish an APK only after the build succeeds.

## Safety boundary

This is a research/implementation note, not a claim that the patch has been implemented or tested. No APK is produced by this document.
