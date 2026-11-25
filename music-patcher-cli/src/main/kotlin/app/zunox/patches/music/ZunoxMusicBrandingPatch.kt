@file:Suppress("unused")

package app.zunox.patches.music

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.io.File

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private const val TARGET_PACKAGE = "com.google.android.apps.youtube.music"
private const val TARGET_VERSION = "9.47.51"
private const val ZUNOX_PACKAGE = "com.zunox.music.v2"

private val zunoxMusicLogo = """
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="256dp"
        android:height="256dp"
        android:viewportWidth="256"
        android:viewportHeight="256">
        <path android:fillColor="#00000000" android:strokeColor="#AFC4D3"
            android:strokeWidth="13" android:pathData="M128,20 A108,108 0,1 1,127.9,20" />
        <path android:fillColor="#00000000" android:strokeColor="#EAF3F8"
            android:strokeWidth="4" android:pathData="M128,32 A96,96 0,1 1,127.9,32" />
        <path android:fillColor="#00000000" android:strokeColor="#71899C"
            android:strokeWidth="21" android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M19,137 C52,137 54,91 79,91 C105,91 98,174 128,174 C158,174 151,72 179,72 C207,72 201,137 237,137" />
        <path android:fillColor="#00000000" android:strokeColor="#C7D9E5"
            android:strokeWidth="12" android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M19,128 C52,128 54,82 79,82 C105,82 98,165 128,165 C158,165 151,63 179,63 C207,63 201,128 237,128" />
        <path android:fillColor="#00000000" android:strokeColor="#F8FCFF"
            android:strokeWidth="3.5" android:strokeLineCap="round" android:strokeLineJoin="round"
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
 * Gives the patched music app an isolated ZUNOX application identity and
 * replaces its launcher and in-app brand resources.
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

            // Give ZUNOX its own Android application ID so it can coexist with
            // the official YouTube Music installation on the same device.
            manifest.documentElement.setAttribute("package", ZUNOX_PACKAGE)

            // The original YouTube Music APK may request Google's shared UID.
            // A third-party release key cannot join that UID, so Android rejects
            // installation with a package/signature conflict even after renaming
            // the package. ZUNOX must use its own isolated application UID.
            manifest.documentElement.removeAttributeNS(ANDROID_NAMESPACE, "sharedUserId")
            manifest.documentElement.removeAttribute("android:sharedUserId")
            manifest.documentElement.removeAttributeNS(ANDROID_NAMESPACE, "sharedUserMaxSdkVersion")
            manifest.documentElement.removeAttribute("android:sharedUserMaxSdkVersion")

            manifest.documentElement.setAttributeNS(ANDROID_NAMESPACE, "android:versionCode", "94751244")
            manifest.documentElement.setAttributeNS(ANDROID_NAMESPACE, "android:versionName", "9.47.55")

            application.setAttributeNS(ANDROID_NAMESPACE, "android:label", "ZUNOX")
            application.setAttributeNS(ANDROID_NAMESPACE, "android:icon", "@mipmap/zunox_launcher")
            application.setAttributeNS(ANDROID_NAMESPACE, "android:roundIcon", "@mipmap/zunox_launcher")

            // Rewrite provider authorities explicitly using both namespace-aware
            // and qualified-name access. Android treats authorities as globally
            // unique, so leaving even one Google authority causes install conflicts.
            val providers = manifest.getElementsByTagName("provider")
            for (index in 0 until providers.length) {
                val provider = providers.item(index) as? Element ?: continue
                // The patcher's XML DOM can expose Android attributes as either
                // namespace-aware nodes or literal "android:*" attributes.
                // Walk the actual attribute map and update the serialized node.
                val attributes = provider.attributes
                for (attributeIndex in 0 until attributes.length) {
                    val attribute = attributes.item(attributeIndex)
                    if (attribute.nodeName != "android:authorities" &&
                        !(attribute.namespaceURI == ANDROID_NAMESPACE && attribute.localName == "authorities")
                    ) continue

                    val oldValue = attribute.nodeValue.orEmpty()
                    val rewritten = oldValue.split(';').joinToString(";") { authority ->
                        if (authority == TARGET_PACKAGE || authority.startsWith("$TARGET_PACKAGE.")) {
                            ZUNOX_PACKAGE + authority.removePrefix(TARGET_PACKAGE)
                        } else authority
                    }
                    if (rewritten != oldValue) {
                        attribute.nodeValue = rewritten
                        provider.setAttribute("android:authorities", rewritten)
                        provider.setAttributeNS(ANDROID_NAMESPACE, "android:authorities", rewritten)
                    }
                }
            }

            // Provider authorities and custom permission names must also be unique.
            val nodes = manifest.getElementsByTagName("*")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                // Android package-scoped declarations must be isolated from Google
                // and from every previous ZUNOX package. Authorities may contain
                // multiple semicolon-separated values; rewrite every occurrence.
                for (attribute in listOf(
                    "authorities", "permission", "readPermission", "writePermission",
                    "taskAffinity", "name"
                )) {
                    if (!element.hasAttributeNS(ANDROID_NAMESPACE, attribute)) continue
                    if (attribute == "name" &&
                        element.tagName !in setOf(
                            "permission", "uses-permission", "uses-permission-sdk-23",
                            "uses-permission-sdk-m"
                        )
                    ) continue

                    val oldValue = element.getAttributeNS(ANDROID_NAMESPACE, attribute)
                    val rewritten = oldValue.split(';').joinToString(";") { entry ->
                        if (entry == TARGET_PACKAGE || entry.startsWith("$TARGET_PACKAGE.")) {
                            ZUNOX_PACKAGE + entry.removePrefix(TARGET_PACKAGE)
                        } else entry
                    }
                    if (rewritten != oldValue) {
                        element.setAttributeNS(ANDROID_NAMESPACE, "android:$attribute", rewritten)
                    }
                }
            }
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

        // Replace the exact YouTube Music logo resources present in 9.47.51.
        val colorBrandResources = setOf(
            "action_bar_logo_release",
            "action_bar_logo_ringo2",
            "action_bar_logo_ringo3",
            "ytm_logo_ringo2",
            "youtube_music_logo_short",
            "splash_screen",
            "audio_banner_app_icon",
            "widget_default_icon"
        )
        val monochromeBrandResources = setOf(
            "ic_app_icons_themed_youtube_music",
            "ic_stat_ytmusic_white",
            "music_push_notification_white"
        )
        val brandResourceNames = colorBrandResources + monochromeBrandResources
        val matchingFiles = resourceRoot.walkTopDown()
            .filter { file ->
                file.isFile && brandResourceNames.any { name ->
                    file.name == name || file.name.startsWith("$name.")
                }
            }
            .toList()
        if (matchingFiles.isEmpty()) {
            throw IllegalStateException("YouTube Music 9.47.51 branding resources were not found.")
        }

        val resourceTypes = matchingFiles.mapNotNull { file ->
            file.parentFile.name.substringBefore('-')
                .takeIf { it == "drawable" || it == "mipmap" }
        }.toSet() + "drawable"

        val generatedBrandPaths = brandResourceNames.flatMap { name ->
            resourceTypes.map { type -> "res/$type/$name.xml" }
        }.toSet()

        matchingFiles.forEach { file ->
            val relativePath = file.relativeTo(resourceRoot.parentFile).path
                .replace(File.separatorChar, '/')
            if (relativePath !in generatedBrandPaths) {
                delete(relativePath)
                // Ensure density-qualified source files are physically removed as well.
                if (file.exists() && !file.delete()) {
                    throw IllegalStateException("Could not remove old YouTube Music logo: $relativePath")
                }
            }
        }

        colorBrandResources.forEach { name ->
            resourceTypes.forEach { type ->
                val output = get("res/$type/$name.xml")
                output.parentFile.mkdirs()
                output.writeText(zunoxMusicLogo)
            }
        }

        monochromeBrandResources.forEach { name ->
            resourceTypes.forEach { type ->
                val output = get("res/$type/$name.xml")
                output.parentFile.mkdirs()
                output.writeText(zunoxMonochromeLogo)
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
