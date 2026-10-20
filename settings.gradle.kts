pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AnonPDF"
// The Flatpak builds the desktop app alone, offline, in a sandbox with no
// Android SDK, so it leaves the Android app out.
if (providers.gradleProperty("anonpdf.desktopOnly").orNull != "true") {
    include(":app")
}
// The PDF engine compiled for desktop. Its source is shared-engine/, which is
// not a module of its own: :app and :desktop-engine each compile it.
include(":desktop-engine")
// The desktop app (Windows, Linux): a native window, no browser, no network.
include(":desktop")
