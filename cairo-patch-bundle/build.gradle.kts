plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.2.21"
}

group = "app.zunox.patches"
version = "0.1.0"

android {
    namespace = "app.zunox.cairo.bundle"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.zunox.cairo.bundle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = project.version.toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xallow-unstable-dependencies",
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters"
        )
    }
}

dependencies {
    compileOnly("app.revanced:patcher-android:22.0.2-dev.1")
    implementation("com.android.tools.smali:smali:3.0.9")
}

val bundleStaging = layout.buildDirectory.dir("bundle-staging")

tasks.register<Exec>("extractPatchDex") {
    dependsOn("assembleDebug")

    val apkDirectory = layout.buildDirectory.dir("outputs/apk/debug")
    val dexDirectory = bundleStaging

    inputs.dir(apkDirectory)
    outputs.dir(dexDirectory)

    doFirst {
        dexDirectory.get().asFile.mkdirs()
    }

    commandLine(
        "bash",
        "-lc",
        listOf(
            "set -euo pipefail",
            "apk=\$(find '${apkDirectory.get().asFile.absolutePath}' -maxdepth 1 -type f -name '*.apk' -print -quit)",
            "test -n \"\$apk\"",
            "staging='${dexDirectory.get().asFile.absolutePath}'",
            "rm -f \"\$staging\"/classes*.dex",
            "dex_entries=\$(unzip -Z1 \"\$apk\" | grep -E '^classes([0-9]+)?\\.dex\$' || true)",
            "test -n \"\$dex_entries\" || { echo 'No DEX files found in patch bundle APK.'; exit 1; }",
            "while IFS= read -r dex; do unzip -p \"\$apk\" \"\$dex\" > \"\$staging/\$dex\"; test -s \"\$staging/\$dex\"; echo \"Extracted \$dex\"; done <<< \"\$dex_entries\""
        ).joinToString("\n")
    )
}

tasks.register<Zip>("packagePatchBundle") {
    dependsOn("extractPatchDex")
    from(bundleStaging)
    from("src/main/bundle")
    archiveFileName.set("zunox-cairo-patches-0.1.0.jar")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/patch-bundle"))
}
