plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.spotless)
}

// Whole-repo formatting: google-java-format for Java, ktfmt for Kotlin and the
// Gradle scripts. Both are opinionated and non-configurable, so nothing is
// formatted by hand. `./gradlew spotlessApply` rewrites, `spotlessCheck` verifies.
//
// Configured once at the root (rather than per module) so the included
// build-logic build is covered too. Generated sources are skipped by the
// marker in their license header so re-running codegen stays diff-clean
// against the generator's own output.
val generatedSourceMarker = "This file is auto-generated"
val nonSourceDirs = listOf("**/build/**", "**/node_modules/**", ".foliage/**", ".kotlin/**")

spotless {
    java {
        target("**/*.java")
        targetExclude(nonSourceDirs)
        targetExcludeIfContentContains(generatedSourceMarker)
        googleJavaFormat(libs.versions.google.java.format.get())
    }
    kotlin {
        target("**/*.kt")
        targetExclude(nonSourceDirs)
        targetExcludeIfContentContains(generatedSourceMarker)
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(nonSourceDirs)
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}
