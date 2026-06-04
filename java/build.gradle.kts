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
