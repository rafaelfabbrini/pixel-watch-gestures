import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // Kotlin itself is compiled by AGP's built-in Kotlin support (AGP 9.0+), so the
    // org.jetbrains.kotlin.android plugin is deliberately NOT applied here.
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.rfabbrini.wristmarkread"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rfabbrini.wristmarkread"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // A debug keystore checked into the repository, replacing the per-machine one AGP
        // auto-generates in ~/.android. Without this every CI runner signs with a fresh throwaway
        // key, so each downloaded APK has a different signature and installing one over the
        // previous build fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE - forcing an uninstall that
        // also drops the app's notification-access grant.
        //
        // Safe to commit: this signs debug builds only. Its password is the well-known Android
        // default, it grants no privileges, and it can never be used to update a Play Store
        // listing. Release builds are unaffected and remain unsigned here.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)

    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
