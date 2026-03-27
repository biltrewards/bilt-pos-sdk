plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.plugins.kotlin.jvm.toDep())
    compileOnly(libs.plugins.maven.publish.toDep())
}

gradlePlugin {
    plugins {
        register("javaLibrary") {
            id = "com.bilt.pos.java.library"
            implementationClass = "JavaLibraryConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "com.bilt.pos.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("mavenPublish") {
            id = "com.bilt.pos.maven.publish"
            implementationClass = "MavenPublishConventionPlugin"
        }
    }
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}
