// Plugins are declared here and applied in the modules that need them, so every module
// resolves the same versions. The :core module is Kotlin Multiplatform and deliberately has
// no Android plugin at all: keeping the measurement and programming logic free of Android
// types is what lets it be unit tested on any machine without an emulator or an SDK, and
// what lets the same code compile to JavaScript for the measurement lab.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
