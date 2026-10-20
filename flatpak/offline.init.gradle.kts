// The Flatpak builds with no network. flatpak-builder fetches every file in
// maven-sources.json, checks its SHA-256 and lays them out as a Maven repository
// in offline-repository/; this makes it the only place the build looks.
settingsEvaluated {
    val offline = settings.rootDir.resolve("offline-repository")
    pluginManagement.repositories {
        clear()
        maven { url = offline.toURI() }
    }
    dependencyResolutionManagement.repositories {
        clear()
        maven { url = offline.toURI() }
    }
}
