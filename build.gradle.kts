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

/**
 * Use the Node and Yarn already on the machine rather than downloading them.
 *
 * Building the measurement lab's browser bundle makes the Kotlin JS plugin fetch its own
 * Node and Yarn, and it registers the download locations as **project** repositories. This
 * build sets `FAIL_ON_PROJECT_REPOS` in settings — deliberately, so that every artifact
 * resolves from one declared list — and the two are irreconcilable: the plugin adds the
 * repository unconditionally, so no settings-level declaration prevents it.
 *
 * Turning the download off removes the conflict at its source instead of weakening the
 * policy for the whole build. CI runners ship both tools, and nothing an Android developer
 * builds touches either — only the JS distribution tasks do, and those are the lab's.
 */
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    extensions.getByType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>()
        .download = false
}

plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    extensions.getByType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>()
        .download = false
}
