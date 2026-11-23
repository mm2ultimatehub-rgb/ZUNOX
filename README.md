# ZUNOX

ZUNOX is an Android patching manager built on the ReVanced Manager codebase and ReVanced patching ecosystem.

The project keeps the upstream patching, APK processing, download, signing, and installation workflows while developing an independent ZUNOX identity and user interface.

## Project status

ZUNOX is under active development. Features and interface are being adapted for the ZUNOX brand; builds may be experimental until a release is published.

## Build

This project uses Gradle and the Android Gradle Plugin. Open the repository in Android Studio or use the included Gradle wrapper:

```bash
./gradlew assembleDebug
```

For release builds, configure the signing environment variables expected by `app/build.gradle.kts`:

- `KEYSTORE_PASSWORD`
- `KEYSTORE_ENTRY_ALIAS`
- `KEYSTORE_ENTRY_PASSWORD`

Do not commit private signing keys or passwords.

## Upstream and licensing

ZUNOX is derived from [ReVanced Manager](https://github.com/ReVanced/revanced-manager). The upstream-derived portions remain under GNU GPL v3. See [LICENSE](LICENSE). Changes and build instructions for distributed modified versions are provided in this repository.

ReVanced names and trademarks belong to their respective owners. ZUNOX is an independent community project and is not an official ReVanced product.
