import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
