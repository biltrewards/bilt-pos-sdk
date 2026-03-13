import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configurePublishing() {
    pluginManager.apply("com.vanniktech.maven.publish")

    group = findProperty("GROUP") as String
    version = findProperty("VERSION") as String

    extensions.configure<MavenPublishBaseExtension> {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
        signAllPublications()

        coordinates(project.group.toString(), "pos-lib-${project.name}", project.version.toString())

        pom {
            name.set("pos-lib-${project.name}")
            description.set("Bilt POS SDK - ${project.name}")
            url.set("https://github.com/biltpos/pretty-porpoise")
            inceptionYear.set("2025")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
                url.set("https://github.com/biltpos/pretty-porpoise")
                connection.set("scm:git:git://github.com/biltpos/pretty-porpoise.git")
                developerConnection.set("scm:git:ssh://git@github.com/biltpos/pretty-porpoise.git")
            }
        }
    }
}
