package app.zunox.cli

import app.revanced.library.ApkUtils.applyTo
import app.revanced.patcher.patch.loadPatches
import app.revanced.patcher.patcher
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun main(args: Array<String>) {
    require(args.size == 4) {
        "Usage: <input.apk> <patches.jar> <output.apk> <android-sdk-root>"
    }

    val input = File(args[0]).absoluteFile
    val patchBundle = File(args[1]).absoluteFile
    val output = File(args[2]).absoluteFile
    val sdkRoot = File(args[3]).absoluteFile
    val aapt = sdkRoot.resolve("build-tools/36.0.0/aapt2")
    val frameworkDirectory = File("build/revanced-framework").absoluteFile
    val temporaryDirectory = File("build/revanced-patcher-temp").absoluteFile

    check(input.isFile) { "Input APK not found: $input" }
    check(patchBundle.isFile) { "Patch bundle not found: $patchBundle" }
    check(aapt.isFile) { "Android aapt2 binary not found: $aapt" }

    val patches = loadPatches(patchBundle) { file, error ->
        System.err.println("Failed to load patch bundle $file")
        error.printStackTrace()
    }.toSet()
    check(patches.isNotEmpty()) { "No patches were loaded from $patchBundle" }

    var patchFailure: Throwable? = null
    val applyPatches = patcher(
        apkFile = input,
        temporaryFilesPath = temporaryDirectory,
        aaptBinaryPath = aapt,
        frameworkFileDirectory = frameworkDirectory.absolutePath
    ) { packageName, versionName ->
        println("Input package: $packageName")
        println("Input version: $versionName")

        patches.filterTo(linkedSetOf()) { patch ->
            val compatiblePackages = patch.compatiblePackages ?: return@filterTo true
            compatiblePackages.any { (compatiblePackage, versions) ->
                compatiblePackage == packageName &&
                    (versions == null || versionName in versions)
            }
        }.also { selected ->
            println("Compatible patches: ${selected.joinToString { it.name.orEmpty() }}")
        }
    }

    val result = applyPatches { patchResult ->
        val error = patchResult.exception
        if (error == null) {
            println("PATCH OK: ${patchResult.patch.name}")
        } else {
            System.err.println("PATCH FAILED: ${patchResult.patch.name}")
            error.printStackTrace()
            patchFailure = error
        }
    }

    patchFailure?.let { throw IllegalStateException("ZUNOX patching failed.", it) }

    output.parentFile?.mkdirs()
    Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    result.applyTo(output)

    println("Patched APK written to $output")
}
