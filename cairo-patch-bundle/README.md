# ZUNOX Cairo splash patch bundle

This isolated Android build produces a ReVanced Patcher-compatible patch bundle JAR for YouTube Music **9.30.53**.

## Patch behavior

The patch identifies the verified `MusicActivity.onCreate(Bundle)` startup method, follows the
Lottie view lookup back to the overlay root returned by `LayoutInflater.inflate`, then sets that
root view to `View.GONE` before it is attached to the activity content.

It does **not** return early from `Activity.onCreate`, remove the rest of the startup method, or
add a Morphe runtime dependency. The patch fails closed if the expected method, view hierarchy,
register layout, or safe temporary register cannot be proven.

## Build

The GitHub Actions workflow `Build ZUNOX Cairo Patch Bundle` compiles the patch and packages its
DEX as `zunox-cairo-patches-0.1.0.jar`. The ZUNOX host app already provides ReVanced Patcher
22.0.2-dev.1 at runtime, so the Patcher dependency is compile-only and is not bundled twice.

This bundle is a development build until the clean APK has been patched and the resulting app has
been installed and tested on a device.
