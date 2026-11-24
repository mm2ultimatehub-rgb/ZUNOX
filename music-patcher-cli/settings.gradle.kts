pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials(PasswordCredentials::class)
        }
    }
}

rootProject.name = "zunox-music-patcher-cli"
