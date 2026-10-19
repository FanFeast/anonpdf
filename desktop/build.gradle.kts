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
    }
}
