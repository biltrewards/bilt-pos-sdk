import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get

fun Project.configurePublishing() {
    pluginManager.apply("maven-publish")
    pluginManager.apply("com.google.cloud.artifactregistry.gradle-plugin")

    group = findProperty("GROUP") as String
    version = findProperty("VERSION") as String

    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])

                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()
                }
            }
            repositories {
                maven {
                    url = uri("artifactregistry://us-maven.pkg.dev/single-scholar-280421/bilt-maven")
                }
            }
        }
    }
}
