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

dependencies {
    // api: the engine's functions hand out PDFBox types, so its users compile against them.
    api(libs.pdfbox.desktop)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
