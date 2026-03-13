plugins {
    id("com.bilt.pos.java.library")
    application
}

// CLI is an application, not a published library
tasks.withType<AbstractPublishToMaven>().configureEach { enabled = false }
tasks.withType<Sign>().configureEach { enabled = false }
tasks.withType<GenerateModuleMetadata>().configureEach { enabled = false }

dependencies {
    implementation(projects.java)
}

application {
    mainClass.set("com.bilt.pos.nexo.cli.Main")
}
