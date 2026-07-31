import java.util.Properties

apply(from = "mediapipe-models.gradle.kts")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Local signing and API credentials, read from a gitignored `keystore.properties` at the
 * repository root. CI supplies the same values through environment variables instead, so
 * no secret ever needs to be committed for a release build to work.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** Environment first (CI), then keystore.properties (local), then a default. */
fun secret(name: String, default: String = ""): String =
    System.getenv(name) ?: localProperties.getProperty(name) ?: default

// Google's documented test IDs. They are the default so that a fresh clone builds and runs
// without credentials, and — more importantly — so a build that was *meant* to carry real
// IDs but lost them falls back to test traffic rather than live traffic. Serving live ads
// from a debug or CI build is how AdMob accounts get suspended for invalid traffic.
val testAdAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdBanner = "ca-app-pub-3940256099942544/6300978111"
val testAdInterstitial = "ca-app-pub-3940256099942544/1033173712"

val releaseKeystorePath = secret("KEYSTORE_FILE")
val hasReleaseSigning = releaseKeystorePath.isNotBlank() && file(releaseKeystorePath).exists()

android {
    namespace = "com.squeeze.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.squeeze.app"
        minSdk = 26
        targetSdk = 35

        // Overridable from CI so a tagged release can stamp a build number without a commit.
        versionCode = secret("VERSION_CODE", "1").toInt()
        versionName = secret("VERSION_NAME", "0.1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AD_UNIT_BANNER", "\"${secret("AD_UNIT_BANNER", testAdBanner)}\"")
        buildConfigField(
            "String",
            "AD_UNIT_INTERSTITIAL",
            "\"${secret("AD_UNIT_INTERSTITIAL", testAdInterstitial)}\"",
        )

        // Play Console licensing key, used by PurchaseVerifier. Blank disables local
        // verification and falls back to trusting the Play Store's own response, which is
        // the right behaviour for a debug build with no Play Console behind it.
        buildConfigField("String", "PLAY_PUBLIC_KEY", "\"${secret("PLAY_PUBLIC_KEY")}\"")

        // Injected as a resource rather than hardcoded in strings.xml so CI can supply the
        // real application ID; the manifest reads @string/admob_app_id either way.
        resValue("string", "admob_app_id", secret("ADMOB_APP_ID", testAdAppId))
    }

    signingConfigs {
        create("release") {
            // Guarded: configuring a signing config with null paths fails the build outright,
            // so an unconfigured clone must skip it rather than half-populate it.
            if (hasReleaseSigning) {
                storeFile = file(releaseKeystorePath)
                storePassword = secret("KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS")
                keyPassword = secret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Without credentials this produces an unsigned release build, which is still
            // useful for checking that R8 and resource shrinking behave. CI always has them.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
        debug {
            applicationIdSuffix = ".debug"
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
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Room exports its schema so migrations can be diffed and tested. Without a location set
// the export is a build warning and the schema is silently lost, which matters here: this
// app has no cloud backup, so a botched migration is unrecoverable data loss.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // All measurement, trend and programming logic lives here, free of Android types.
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.tink.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.health.connect)

    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
