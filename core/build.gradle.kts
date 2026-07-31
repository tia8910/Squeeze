import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No version here. The root build already puts the Kotlin plugin on the build
    // classpath, and a subproject that re-requests a plugin *with* a version when one is
    // already loaded fails configuration outright:
    //   "the plugin is already on the classpath with an unknown version"
    // The version lives in the catalog, declared once at the root with `apply false`.
    alias(libs.plugins.kotlin.jvm)
}

// Pure JVM module: no Android dependencies, so the measurement and programming logic can
// be tested on any machine without an Android SDK. Bytecode targets 17 to match what the
// Android Gradle Plugin expects, but no specific JDK is required to build it.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
