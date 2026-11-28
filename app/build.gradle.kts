import java.util.Properties

// AGP 9 compiles Kotlin itself, so there is no separate Kotlin Gradle plugin —
// only the Compose compiler plugin, which is still applied explicitly.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is opt-in: drop a keystore.properties in the repo root (it is
// gitignored) and the release build gets signed. Without it the build still
// works, it just produces an unsigned release artifact.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "io.github.fanfeast.anonpdf"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.fanfeast.anonpdf"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v1 (JAR) signing only matters below API 24 and minSdk is 31.
                // v3 adds key-rotation support for direct APK installs.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The About screen shows the version name.
        buildConfig = true
    }

    // The PDF engine lives in shared-engine/ so the desktop app can compile the
    // same source (see desktop-engine/). This module still builds it directly.
    sourceSets {
        getByName("main").kotlin.srcDir("../shared-engine/src/main/kotlin")
        // The screens shared with the desktop app, likewise.
        getByName("main").kotlin.srcDir("../shared-ui/src/main/kotlin")
        getByName("test").kotlin.srcDir("../shared-ui/src/test/kotlin")
        getByName("test").kotlin.srcDir("../shared-engine/src/test/kotlin")
        // Engine tests that need a real renderer run on a device here.
        getByName("androidTest").kotlin.srcDir("../shared-engine/src/deviceTest/kotlin")
        // CI's English language pack for the OCR test, as a test asset; never in the app.
        System.getenv("ANONPDF_TESSDATA")?.let { getByName("androidTest").assets.srcDir(it) }
    }

    packaging {
        // Compressed in the APK and unpacked at install, rather than stored as-is:
        // the recogniser's libraries are most of the app's size, and this roughly
        // halves the download for the APK on the releases page.
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                // GraalVM native-image hints in the JavaCPP jars; nothing reads them on Android.
                "META-INF/native-image/**",
                // C headers and build files the image library's jars carry beside
                // its libraries, for compiling against it. Play rejects them in a bundle.
                "lib/*/include/**",
                "lib/*/lib/**",
                // Tesseract's command-line program; the app calls the library.
                "lib/*/tesseract",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // BouncyCastle comes along with PDFBox but is only reachable from its
    // certificate-based ("public key") encryption handler, which AnonPDF does not
    // expose. Password protection uses AES via the platform's own JCE. Dropping it
    // removes ~4 MB of unused code — most of it post-quantum algorithms — and the
    // protect/unlock instrumented tests prove the remaining paths still work.
    implementation(libs.pdfbox.android) {
        exclude(group = "org.bouncycastle")
    }

    // Text recognition: Tesseract, run in-process from the native library inside
    // the app. 64-bit only, as JavaCPP no longer builds 32-bit ARM: on a 32-bit
    // phone the app installs and works as before, and says OCR isn't available.
    implementation(libs.tesseract)
    listOf("android-arm64", "android-x86_64").forEach { platform ->
        listOf(libs.javacpp, libs.leptonica, libs.tesseract).forEach { lib ->
            implementation(variantOf(lib) { classifier(platform) })
        }
    }

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
