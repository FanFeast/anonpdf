// The PDF engine, compiled for desktop (Windows, Linux) against upstream PDFBox.
//
// The engine's source is written once, in shared-engine/, against pdfbox-android:
// Android's port of PDFBox, with the same API under the package
// com.tom_roush.pdfbox. This module compiles a copy with the upstream package
// name put back. The copy is generated on every build, so there is nothing to
// keep in sync by hand. Only what differs per platform (how a page becomes an
// image, which image type that is) lives here, in src/main.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

fun portedFromAndroid(name: String, from: String) = tasks.register<Sync>("port${name}Engine") {
    from(rootProject.file(from))
    into(layout.buildDirectory.dir("generated/shared-engine/$name"))
    filter { line: String -> line.replace("com.tom_roush.pdfbox", "org.apache.pdfbox") }
}

val portMain = portedFromAndroid("Main", "shared-engine/src/main/kotlin")
val portTest = portedFromAndroid("Test", "shared-engine/src/test/kotlin")
// On Android these need a device for its renderer; on desktop they are plain tests.
val portDeviceTest = portedFromAndroid("DeviceTest", "shared-engine/src/deviceTest/kotlin")

sourceSets {
    main { kotlin.srcDir(portMain) }
    test {
        kotlin.srcDir(portTest)
        kotlin.srcDir(portDeviceTest)
    }
}

// Tesseract's native libraries for the machine doing the build: CI builds each
// platform's download on that platform, so each carries only its own.
val nativePlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x86_64"
    when {
        os.startsWith("windows") -> "windows-$arch"
        os.startsWith("mac") -> "macosx-$arch"
        else -> "linux-$arch"
    }
}

dependencies {
    // api: the engine's functions hand out PDFBox types, so its users compile against them.
    api(libs.pdfbox.desktop)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.tesseract)
    listOf(libs.javacpp, libs.leptonica, libs.tesseract).forEach { lib ->
        runtimeOnly(variantOf(lib) { classifier(nativePlatform) })
    }
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // Where JavaCPP unpacks Tesseract, instead of ~/.javacpp.
    systemProperty("org.bytedeco.javacpp.cachedir", layout.buildDirectory.dir("javacpp").get().asFile.absolutePath)
    // The OCR tests need a language pack; CI downloads one and says where.
    System.getenv("ANONPDF_TESSDATA")?.let { systemProperty("anonpdf.tessdata", it) }
    // CI's log is all there is of a failed run, so it carries the assertion's message.
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
