package app.zunox.cli

import app.revanced.library.ApkUtils.applyTo
import app.revanced.patcher.patch.loadPatches
import app.revanced.patcher.patcher
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private fun sanitizeInvalidResourceNames(resourceDirectory: File) {
    if (!resourceDirectory.isDirectory) return

    val renameMap = linkedMapOf<String, String>()
    val resourceFiles = resourceDirectory.walkTopDown().filter { it.isFile }.toList()

    resourceFiles.forEach { file ->
        val oldName = file.nameWithoutExtension
        if ('$' !in oldName) return@forEach

        val normalized = oldName.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val baseName = "zunox_\${normalized}"
        var newName = baseName
        var suffix = 1
        val extension = file.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""

        while (File(file.parentFile, "$newName$extension").exists()) {
            newName = "\${baseName}_$suffix"
            suffix++
        }

        val renamedFile = File(file.parentFile, "$newName$extension")
        check(file.renameTo(renamedFile)) {
            "Could not normalize invalid Android resource filename: \${file.path}"
        }
        renameMap[oldName] = newName
    }

    if (renameMap.isEmpty()) return

    val referenceTypes = listOf(
        "drawable", "mipmap", "anim", "animator", "raw", "xml",
        "layout", "menu", "color", "interpolator", "font", "transition", "navigation"
    )
    resourceDirectory.walkTopDown()
        .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
        .toList()
        .forEach { xmlFile ->
            var xml = xmlFile.readText()
            renameMap.forEach { (oldName, newName) ->
                referenceTypes.forEach { type ->
                    xml = xml.replace("@$type/$oldName", "@$type/$newName")
                }
                xml = xml.replace("name=\"$oldName\"", "name=\"$newName\"")
            }
            xmlFile.writeText(xml)
        }

    println("Normalized \${renameMap.size} invalid resource filenames before AAPT2 compilation.")
}

fun main(args: Array<String>) {
    require(args.size == 4) {
        "Usage: <input.apk> <patches.jar> <output.apk> <android-sdk-root>"
    }

    val input = File(args[0]).absoluteFile
    val patchBundle = File(args[1]).absoluteFile
    val output = File(args[2]).absoluteFile
    val sdkRoot = File(args[3]).absoluteFile
    val aapt2 = sdkRoot.resolve("build-tools/36.0.0/aapt2")
    val frameworkDirectory = File("build/revanced-framework").absoluteFile
    val temporaryDirectory = File("build/revanced-patcher-temp").absoluteFile

    check(input.isFile) { "Input APK not found: $input" }
    check(patchBundle.isFile) { "Patch bundle not found: $patchBundle" }
    check(aapt2.isFile) { "Android aapt2 binary not found: $aapt2" }

    val patches = loadPatches(patchBundle) { file, error ->
        System.err.println("Failed to load patch bundle $file")
        error.printStackTrace()
    }.toSet()
    check(patches.isNotEmpty()) { "No patches were loaded from $patchBundle" }

    var patchFailure: Throwable? = null
    val applyPatches = patcher(
        apkFile = input,
        temporaryFilesPath = temporaryDirectory,
        aaptBinaryPath = aapt2,
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
            println("Compatible patches: \${selected.joinToString { it.name.orEmpty() }}")
        }
    }

    val result = applyPatches { patchResult ->
        val error = patchResult.exception
        if (error == null) {
            println("PATCH OK: \${patchResult.patch.name}")
        } else {
            System.err.println("PATCH FAILED: \${patchResult.patch.name}")
            error.printStackTrace()
            patchFailure = error
        }
    }

    patchFailure?.let { throw IllegalStateException("ZUNOX patching failed.", it) }

    // Apktool exposes a few framework animation resources with '$' in their
    // generated filenames. AAPT2 rejects those names, so normalize filenames
    // and every XML/public.xml reference before the resource table is rebuilt.
    sanitizeInvalidResourceNames(File(temporaryDirectory, "apk/res"))

    output.parentFile?.mkdirs()
    Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    result.applyTo(output)

    println("Patched APK written to $output")
}
