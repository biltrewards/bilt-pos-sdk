plugins {
    id("com.bilt.pos.java.library")
    application
}

dependencies {
    implementation(projects.java)
}

application {
    mainClass.set("com.bilt.pos.nexo.cli.Main")
}
