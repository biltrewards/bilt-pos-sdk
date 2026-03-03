plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.plugins.kotlin.jvm.toDep())
    implementation(libs.plugins.artifact.registry.toDep())
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
    }
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}
