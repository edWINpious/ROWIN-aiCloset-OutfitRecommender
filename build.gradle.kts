// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose.plugin) apply false
    alias(libs.plugins.ksp) apply false
    // Applied per-module only when google-services.json exists (see app/build.gradle.kts)
    alias(libs.plugins.google.services) apply false
}

// AGP 9.3.3 bundles Kotlin 2.2.10, but the current Compose artifacts are
// compiled with Kotlin 2.4 metadata. Overriding the Kotlin Gradle plugin on
// the buildscript classpath upgrades the embedded compiler (works upward only).
// Verify with: ./gradlew buildEnvironment | grep kotlin-gradle-plugin
//   expected: org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.10
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Keep in sync with `composeCompiler` in gradle/libs.versions.toml.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}
