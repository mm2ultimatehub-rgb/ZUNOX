plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    application
}

group = "app.zunox"
version = "0.1.0"

dependencies {
    implementation("app.revanced:patcher:22.0.2-dev.1")
    implementation("app.revanced:library:4.0.0")
    implementation("com.android.tools.smali:smali:3.0.9")
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

application {
    mainClass.set("app.zunox.cli.MainKt")
}

tasks.jar {
    archiveBaseName.set("zunox-music-patches")
    manifest {
        attributes(
            "name" to "ZUNOX Music Patches",
            "version" to project.version.toString(),
            "description" to "Cairo splash removal and ZUNOX branding for YouTube Music 9.47.51.",
            "source" to "https://github.com/mm2ultimatehub-rgb/ZUNOX",
            "author" to "ZUNOX",
            "website" to "https://github.com/mm2ultimatehub-rgb/ZUNOX",
            "license" to "GPL-3.0"
        )
    }
}
