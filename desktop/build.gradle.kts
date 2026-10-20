// The desktop app: a native Compose window for Windows and Linux. It runs the
// same PDF engine as the Android app (desktop-engine), opens no port and makes
// no network connection.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

// The screens shared with the Android app. Compose Multiplatform uses the same
// androidx.compose packages as Jetpack Compose, so they compile here unchanged.
sourceSets {
    main { kotlin.srcDir(rootProject.file("shared-ui/src/main/kotlin")) }
    test { kotlin.srcDir(rootProject.file("shared-ui/src/test/kotlin")) }
}

dependencies {
    implementation(project(":desktop-engine"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.desktop.material3)
    implementation(libs.compose.desktop.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose.desktop)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(libs.junit)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

compose.desktop {
    application {
        mainClass = "io.github.fanfeast.anonpdf.desktop.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
            // Shrink only: names stay readable in crash traces, and PDFBox's
            // by-name lookups keep working.
            obfuscate.set(false)
        }

        nativeDistributions {
            packageName = "AnonPDF"
            // CI passes the tag's version; jpackage needs plain numbers (no "v", no suffix).
            packageVersion = (findProperty("appVersion") as String?) ?: "1.0.0"
            description = "Private PDF editor. Nothing leaves your computer."
            vendor = "AnonPDF"
            // Only what the app uses, beyond java.base and java.desktop, which are
            // always in: a smaller download, and no HTTP client or HTTP server in
            // the bundled runtime at all.
            modules("java.instrument", "java.logging", "java.xml", "jdk.unsupported")
            linux { iconFile.set(project.file("packaging/anonpdf.png")) }
            windows { iconFile.set(project.file("packaging/anonpdf.ico")) }
        }
    }
}
