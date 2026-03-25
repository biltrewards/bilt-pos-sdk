import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configurePublishing() {
    pluginManager.apply("com.vanniktech.maven.publish")

    group = findProperty("GROUP") as String
    version = findProperty("VERSION") as String

    extensions.configure<MavenPublishBaseExtension> {
        publishToMavenCentral()
        signAllPublications()

        coordinates(project.group.toString(), "pos-lib-${project.name}", project.version.toString())

        pom {
            name.set("pos-lib-${project.name}")
            description.set("Bilt POS SDK - ${project.name}")
            url.set("https://github.com/biltrewards/bilt-pos-sdk")
            inceptionYear.set("2025")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }

            developers {
                developer {
                    id.set("biltpos")
                    name.set("Bilt POS")
                    url.set("https://github.com/biltpos")
                }
            }

            scm {
                url.set("https://github.com/biltrewards/bilt-pos-sdk")
                connection.set("scm:git:git://github.com/biltrewards/bilt-pos-sdk.git")
                developerConnection.set("scm:git:ssh://git@github.com/biltrewards/bilt-pos-sdk.git")
            }
        }
    }
}
