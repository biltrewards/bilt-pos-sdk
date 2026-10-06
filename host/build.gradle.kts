plugins {
    id("com.bilt.pos.java.library")
    application
}

// The convention plugin pins the SDK to Java 11 so it still runs on the oldest
// registers. The host embeds Javalin 7 on Jetty 12, which needs 17, and only
// ever runs where a modern browser runs, so it raises the level for itself.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(projects.java)
    implementation(libs.javalin)
}

// DevMain starts the host from a small JSON terminal config so the protocol can
// be driven with curl before the Terminal Bridge exists:
//   ./gradlew :host:run --args="path/to/terminals.json"
application {
    mainClass.set("com.bilt.pos.host.DevMain")
}

// Stamps the release into bilt-pos-host-version.properties so GET /health can
// report hostVersion without a hand-maintained constant.
tasks.processResources {
    val hostVersion = providers.gradleProperty("VERSION")
    inputs.property("hostVersion", hostVersion)
    filesMatching("bilt-pos-host-version.properties") {
        expand(mapOf("hostVersion" to hostVersion.get()))
    }
}
