// Plugins are declared here and applied in the modules that need them, so every module
// resolves the same versions. The :core module is plain Kotlin/JVM and deliberately has no
// Android plugin at all: keeping the measurement and programming logic free of Android
// types is what lets it be unit tested on any machine, without an emulator or an SDK.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
