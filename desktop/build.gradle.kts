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

dependencies {
    implementation(project(":desktop-engine"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.desktop.material3)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "io.github.fanfeast.anonpdf.desktop.MainKt"
    }
}
