plugins {
    id("com.bilt.pos.java.library")
}

dependencies {
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
}
