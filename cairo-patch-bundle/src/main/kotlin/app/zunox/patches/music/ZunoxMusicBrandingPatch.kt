@file:Suppress("unused")

package app.zunox.patches.music

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.io.File

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private const val TARGET_PACKAGE = "com.google.android.apps.youtube.music"
private const val TARGET_VERSION = "9.30.53"

private val zunoxMusicLogo = """
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="256dp"
        android:height="256dp"
        android:viewportWidth="256"
        android:viewportHeight="256">
        <path
            android:fillColor="#00000000"
            android:strokeColor="#9EB4C5"
            android:strokeWidth="12"
            android:pathData="M128,18 A110,110 0,1 1,127.9,18" />
        <path
            android:fillColor="#00000000"
            android:strokeColor="#EAF3F8"
            android:strokeWidth="5"
            android:pathData="M128,31 A97,97 0,1 1,127.9,31" />
        <path
            android:fillColor="#00000000"
            android:strokeColor="#71899C"
            android:strokeWidth="19"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:pathData="M19,137 C52,137 54,91 79,91 C105,91 98,174 128,174 C158,174 151,72 179,72 C207,72 201,137 237,137" />
        <path
            android:fillColor="#00000000"
            android:strokeColor="#F8FCFF"
            android:strokeWidth="10"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:pathData="M19,128 C52,128 54,82 79,82 C105,82 98,165 128,165 C158,165 151,63 179,63 C207,63 201,128 237,128" />
        <path
            android:fillColor="#00000000"
            android:strokeColor="#C5D7E4"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M19,123 C52,123 54,77 79,77 C105,77 98,160 128,160 C158,160 151,58 179,58 C207,58 201,123 237,123" />
    </vector>
""".trimIndent()

private val zunoxMonochromeLogo = """
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="256dp"
        android:height="256dp"
        android:viewportWidth="256"
        android:viewportHeight="256">
        <path android:fillColor="#FFFFFFFF" android:pathData="M128,18 A110,110 0,1 1,127.9,18" />
        <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="19"
            android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M19,128 C52,128 54,82 79,82 C105,82 98,165 128,165 C158,165 151,63 179,63 C207,63 201,128 237,128" />
    </vector>
""".trimIndent()

/**
 * Gives the patched music app a ZUNOX identity while retaining the original
 * package identifier required by the target app's existing service integration.
 */
val zunoxMusicBrandingPatch = resourcePatch(
    name = "ZUNOX app identity",
    description = "Replaces the launcher name, launcher icon, and music header marks with ZUNOX branding."
) {
    compatibleWith(TARGET_PACKAGE to setOf(TARGET_VERSION))
    dependsOn(disableCairoSplashAnimationPatch)

    apply {
        // Update the Android launcher label and icon without changing the package ID.
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: throw IllegalStateException("AndroidManifest application element was not found.")

            application.setAttributeNS(ANDROID_NAMESPACE, "android:label", "ZUNOX")
            application.setAttributeNS(ANDROID_NAMESPACE, "android:icon", "@mipmap/zunox_launcher")
            application.setAttributeNS(ANDROID_NAMESPACE, "android:roundIcon", "@mipmap/zunox_launcher")
        }

        // Replace app-name strings in every available locale.
        val resourceRoot = get("res")
        resourceRoot.walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" && it.parentFile.name.startsWith("values") }
            .toList()
            .forEach { file ->
                val relativePath = file.relativeTo(resourceRoot.parentFile).path
                    .replace(File.separatorChar, '/')
                document(relativePath).use { xml ->
                    val strings = xml.getElementsByTagName("string")
                    for (index in 0 until strings.length) {
                        val element = strings.item(index) as? Element ?: continue
                        if (element.getAttribute("name") in setOf("app_name", "app_launcher_name")) {
                            element.textContent = "ZUNOX"
                        }
                    }
                }
            }

        // Replace the known YouTube Music header wordmark resources with the ZUNOX mark.
        val headerResourceNames = setOf("logo_music", "ytm_logo", "action_bar_logo")
        val matchingFiles = resourceRoot.walkTopDown()
            .filter { file ->
                file.isFile && headerResourceNames.any { name ->
                    file.name == name || file.name.startsWith("$name.")
                }
            }
            .toList()

        val resourceTypes = matchingFiles.mapNotNull { file ->
            file.parentFile.name.substringBefore('-')
                .takeIf { it == "drawable" || it == "mipmap" }
        }.toSet() + "drawable"

        val generatedHeaderPaths = headerResourceNames.flatMap { name ->
            resourceTypes.map { type -> "res/$type/$name.xml" }
        }.toSet()

        matchingFiles.forEach { file ->
            val relativePath = file.relativeTo(resourceRoot.parentFile).path
                .replace(File.separatorChar, '/')
            if (relativePath !in generatedHeaderPaths) {
                delete(relativePath)
            }
        }

        headerResourceNames.forEach { name ->
            resourceTypes.forEach { type ->
                val output = get("res/$type/$name.xml")
                output.parentFile.mkdirs()
                output.writeText(zunoxMusicLogo)
            }
        }

        // Add a modern adaptive launcher icon using the same ZUNOX identity.
        val background = get("res/values/zunox_branding.xml")
        background.parentFile.mkdirs()
        background.writeText(
            """<resources><color name="zunox_icon_background">#0B1017</color></resources>"""
        )

        val foreground = get("res/drawable/zunox_launcher_foreground.xml")
        foreground.parentFile.mkdirs()
        foreground.writeText(zunoxMusicLogo)

        val monochrome = get("res/drawable/zunox_launcher_monochrome.xml")
        monochrome.parentFile.mkdirs()
        monochrome.writeText(zunoxMonochromeLogo)

        val adaptiveIcon = get("res/mipmap-anydpi-v26/zunox_launcher.xml")
        adaptiveIcon.parentFile.mkdirs()
        adaptiveIcon.writeText(
            """
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@color/zunox_icon_background" />
                    <foreground android:drawable="@drawable/zunox_launcher_foreground" />
                    <monochrome android:drawable="@drawable/zunox_launcher_monochrome" />
                </adaptive-icon>
            """.trimIndent()
        )
    }
}
