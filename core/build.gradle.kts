import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No version here. The root build already puts the Kotlin plugin on the build
    // classpath, and a subproject that re-requests a plugin *with* a version when one is
    // already loaded fails configuration outright:
    //   "the plugin is already on the classpath with an unknown version"
    // The version lives in the catalog, declared once at the root with `apply false`.
    alias(libs.plugins.kotlin.multiplatform)
}

/**
 * The measurement and programming logic, built for two places at once.
 *
 * It was a plain JVM module, and the value of that has not changed: no Android dependency,
 * so every equation here is testable on any machine without an SDK. What multiplatform adds
 * is a second consumer — a browser.
 *
 * That is not a hypothetical. Every measurement bug this pipeline has had was invisible by
 * construction: the waist band read under the ribs, the shoulder run swallowing both arms,
 * the hip band landing on a waistband, the whole frame lying on its side. Rows and bands are
 * numbers here and pixels in a photograph, and nothing in the repository could put the two
 * together — each was found from a screenshot of a percentage, one round trip at a time.
 *
 * MediaPipe publishes a web build of the same models the app ships, so a browser page can
 * run this exact code over a real photograph and draw the bands where they actually land.
 * Sharing the code rather than porting it is the whole point: a TypeScript copy would drift
 * from what ships, and a lab that disagrees with the app is worse than no lab.
 *
 * No `java.*` import appears anywhere in this module, which is what makes the JS target
 * possible at all; `com.squeeze.core.text.Decimals` exists because `String.format` was the
 * one exception.
 */
kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // Set on the target rather than through tasks.withType<Test>, which is what tells
        // the Kotlin plugin to resolve kotlin("test") to its JUnit 5 variant. Configuring
        // the task alone leaves the wrong engine on the classpath and no tests are found.
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            testLogging {
                events("passed", "failed", "skipped")
            }
        }
    }

    // ES modules and an executable binary, because the measurement lab is a plain HTML page
    // that does `import { MeasurementLab } from "./core.mjs"`. A library binary produces a
    // distribution meant to be consumed by another bundler, which would put a build step
    // between the lab and the code it exists to inspect.
    js(IR) {
        useEsModules()
        browser()
        binaries.executable()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
