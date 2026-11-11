import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.monsterquest.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.monsterquest.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 184
        versionName = "3.21.0"
    }

    signingConfigs {
        // A fixed debug key checked into the repo, so every CI build can be
        // installed over the previous one without losing your save.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // What CI publishes: not debuggable, so Android compiles the app ahead of time and
        // Compose's baseline profiles apply. Debug builds scroll and animate far slower.
        // Signed with the same fixed key, so it installs over earlier (debug) builds.
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    lint {
        // The tests and screenshots cover what lint's release checks would; don't block the build on them.
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    // Installs the libraries' baseline profiles (Compose's included) on sideloaded installs too.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Boot splash on every Android version (the Android 12+ splash API, back-ported).
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Home-screen widget (Compose-style, Material 3 colors).
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Foldable support: hinge position and posture (tabletop / book).
    implementation("androidx.window:window:1.3.0")

    // Steps from Health Connect (Samsung Health, Google Fit, Fitbit, ...).
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")

    // OpenStreetMap map view (no API key needed).
    implementation("org.maplibre.gl:android-sdk:13.7.1")

    // Screenshot tests: render screens on the JVM at foldable sizes.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
