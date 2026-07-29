plugins {
    id("com.bilt.pos.java.library")
    id("com.bilt.pos.maven.publish")
}

dependencies {
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.okhttp)

    // JAXB for XML serialization (display/input payloads)
    api(libs.jaxb.api)
    runtimeOnly(libs.jaxb.runtime)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
}

// Browsable Javadoc for the SDK's public surface, served on the docs site
// from docs/javadoc (checked in, like the Redoc api-reference.html).
// Excludes the internal packages and the ~190 generated nexo wire models,
// which would drown the session API. Regenerate after API changes with:
//   ./gradlew :java:publishApiDocs
val publicApiJavadoc = tasks.register<Javadoc>("publicApiJavadoc") {
    group = "documentation"
    description = "Javadoc for the public API (no internal or generated wire model classes)"
    source = sourceSets["main"].allJava
    include(
        "com/bilt/pos/session/**",
        "com/bilt/pos/nexo/client/**",
        "com/bilt/pos/nexo/security/**",
        "com/bilt/pos/display/**",
        "com/bilt/pos/receipt/**",
    )
    exclude("com/bilt/pos/session/internal/**")
    classpath = files(sourceSets["main"].compileClasspath, sourceSets["main"].output)
    setDestinationDir(layout.buildDirectory.dir("docs/public-api").get().asFile)
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        docTitle = "Bilt POS SDK &mdash; Public API"
        windowTitle = "Bilt POS SDK Public API"
        // keep regeneration diffs meaningful: no per-run timestamps
        noTimestamp(true)
        links("https://docs.oracle.com/en/java/javase/11/docs/api/")
        // don't fail or spam on missing @param/@return; other lints stay on
        addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}

tasks.register<Sync>("publishApiDocs") {
    group = "documentation"
    description = "Regenerates docs/javadoc for the docs site"
    from(publicApiJavadoc)
    into(rootProject.layout.projectDirectory.dir("docs/javadoc"))
}
