import org.gradle.api.Plugin
import org.gradle.api.Project

class MavenPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        configurePublishing()
    }
}
