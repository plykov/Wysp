plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.wysp.krysp.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wysp.krysp.android"
        // AudioPlaybackCaptureConfiguration (privileged dual-track path) requires API 29+.
        // The mic+speakerphone fallback would work on older devices, but we target 29+ across
        // the board to keep a single code path simple; lower it if you need older-device support
        // for the fallback-only path.
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    // Uncomment once native/CMakeLists.txt has a real whisper.cpp checkout under
    // third_party/whisper.cpp (see native/README.md) - left disabled so a fresh
    // checkout of this repo still configures without requiring the submodule.
    // externalNativeBuild {
    //     cmake {
    //         path = file("../native/CMakeLists.txt")
    //         version = "3.22.1"
    //     }
    // }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
