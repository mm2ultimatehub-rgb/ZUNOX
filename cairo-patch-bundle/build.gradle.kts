plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.3.21"
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
        release { isMinifyEnabled = false }
    }
}
kotlin {
    jvmToolchain(17)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}
dependencies {
    compileOnly("app.revanced:patcher-android:22.0.2-dev.1")
}
val bundleStaging = layout.buildDirectory.dir("bundle-staging")
tasks.register<Exec>("extractPatchDex") {
    dependsOn("assembleDebug")
    val apk = layout.buildDirectory.file("outputs/apk/debug/cairo-patch-bundle-debug.apk")
    val dex = bundleStaging.map { it.file("classes.dex") }
    inputs.file(apk)
    outputs.file(dex)
    doFirst { bundleStaging.get().asFile.mkdirs() }
    commandLine(
        "bash", "-lc",
        "unzip -p '${apk.get().asFile.absolutePath}' classes.dex > '${dex.get().asFile.absolutePath}'"
    )
}
tasks.register<Zip>("packagePatchBundle") {
    dependsOn("extractPatchDex")
    from(bundleStaging)
    from("src/main/bundle")
    archiveFileName.set("zunox-cairo-patches-0.1.0.jar")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/patch-bundle"))
}
