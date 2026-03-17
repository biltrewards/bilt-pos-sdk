plugins {
    id("com.bilt.pos.java.library")
    id("com.bilt.pos.maven.publish")
}

dependencies {
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
}
