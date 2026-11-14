// Top-level build file. Plugins are declared here once and applied per module.
// Kotlin compilation itself is built into AGP 9 (no kotlin-android plugin);
// the Compose and serialization compiler plugins pin the Kotlin version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
}
