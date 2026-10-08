import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ─── Signing keystore — read from keystore.properties (gitignored) ───
//   The keystore file (coral-release.jks) is also gitignored.
//   This is your PERMANENT signing identity — back it up somewhere safe
//   (cloud, USB, etc.). If you lose it, you can never update the app.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.rajatxo.coral"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rajatxo.coral"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.6"
    }

    // ─── Signing config — uses the permanent keystore for BOTH debug and release ───
    //   This way, debug APKs always have the same signing key → can update over
    //   each other without uninstalling. Same for release builds.
    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            manifestPlaceholders["appName"] = "Coral Debug"
            // ★ Use the release keystore so debug builds always have the same
            //   signing identity → updates install cleanly over previous debug APKs.
            signingConfig = signingConfigs.getByName("release")
        }

        release {
            versionNameSuffix = "-RELEASE"
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appName"] = "Coral"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Disable lintVital — AGP 8.7.3's lint crashes on Kotlin 2.2+
    // (NonNullableMutableLiveDataDetector IncompatibleClassChangeError).
    // This is a known AGP bug, not a code issue.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Media3 decoder — provides software decoders (FFmpeg-based) for codecs
    // that hardware doesn't support (ALAC 24-bit, Dolby Atmos, etc.)
    implementation("androidx.media3:media3-decoder:1.5.1")
    // Pure-Java ALAC decoder — bypasses the broken hardware ALAC decoder
    // that produces silence on 24-bit ALAC files. Same library Gramophone uses.
    implementation(project(":misc:alacdecoder"))
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Liquid glass backdrop blur — same library SimpMusic uses
    // Version 1.0.0 has minCompileSdk=1 (works with compileSdk 35)
    // Later versions (1.0.6, 2.0.0) require SDK 36/37 + AGP 9.x
    implementation("io.github.kyant0:backdrop:1.0.0")
    // Haze — crash-free glass morphism (used by ArchiveTune/BitChord)
    // Much simpler than kyant backdrop: HazeState() + hazeSource + hazeEffect
    implementation("dev.chrisbanes.haze:haze:1.6.9")
    // ★ Lottie — animated cat (tiny JSON, renders natively in Compose)
    implementation("com.airbnb.android:lottie-compose:6.6.2")
}
