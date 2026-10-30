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
include(":app")
// The PDF engine compiled for desktop. Its source is shared-engine/, which is
// not a module of its own: :app and :desktop-engine each compile it.
include(":desktop-engine")
// The desktop app (Windows, Linux): a native window, no browser, no network.
include(":desktop")
