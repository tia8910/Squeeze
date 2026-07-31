plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.squeeze.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.squeeze.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google's documented test ad unit IDs. Real IDs belong in a local properties file
        // that is never committed; shipping a debug build against live units risks the
        // AdMob account being suspended for invalid traffic.
        buildConfigField("String", "AD_UNIT_BANNER", "\"ca-app-pub-3940256099942544/6300978111\"")
        buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")

        // Play Console signing key for local purchase verification. Replace at release time.
        buildConfigField("String", "PLAY_PUBLIC_KEY", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

dependencies {
    // All measurement, trend and programming logic lives here, free of Android types.
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
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

    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.health.connect)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
