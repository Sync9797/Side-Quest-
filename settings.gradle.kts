pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.2.21"
        id("org.jetbrains.kotlin.jvm") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MonsterQuest"

// Pure-Kotlin game engine (builds anywhere, unit tested).
include(":core")

// The Android app needs the Android SDK. Set CORE_ONLY=1 to skip it on
// machines without one.
if (System.getenv("CORE_ONLY") == null) {
    include(":app")
    // AI Widget Maker: a separate app that designs home-screen (and lock-screen) widgets with Claude.
    include(":widgetmaker")
}
