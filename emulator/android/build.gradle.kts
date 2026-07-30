import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 has built-in Kotlin support; the standalone kotlin-android
    // plugin is rejected at configuration time
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.bilt.pos.emulator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bilt.pos.emulator"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // duplicated across the SDK's XML dependency chain
        resources.excludes += "/META-INF/{LICENSE.md,NOTICE.md}"
    }
}

dependencies {
    implementation(projects.emulator.shared)
    implementation(libs.androidx.activity.compose)
}
