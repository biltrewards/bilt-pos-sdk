import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9's KMP library plugin: the android target is declared inside
    // kotlin {} below; there is no separate android {} block
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.bilt.pos.emulator.shared"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // desktop targets 21; the android target stays at 17 (the ART ceiling)
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }

        // Both targets are JVM-bytecode, so the Java SDK is shared here rather
        // than duplicated per target. commonMain stays platform-neutral and
        // must not reference SDK classes directly.
        val jvmShared by creating {
            dependsOn(commonMain.get())
            dependencies {
                api(projects.java)
            }
        }
        androidMain.get().dependsOn(jvmShared)
        getByName("desktopMain").dependsOn(jvmShared)

        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            // skiko natives for headless UI rendering (ScreenshotGenerator)
            implementation(compose.desktop.currentOs)
            // real TLS handshakes against a local server in TlsVerifierTest
            implementation(libs.okhttp.mockwebserver)
            implementation(libs.okhttp.tls)
        }
    }
}
