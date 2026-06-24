import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

allprojects {
    plugins.withId("java") {
        plugins.apply("jacoco")

        extensions.configure(JacocoPluginExtension::class.java) {
            toolVersion = "0.8.14"
        }

        tasks.withType(Test::class.java).configureEach {
            finalizedBy(tasks.named("jacocoTestReport"))
        }

        tasks.named("jacocoTestReport", JacocoReport::class.java) {
            dependsOn(tasks.named("test"))

            reports {
                xml.required = true
                html.required = true
            }
        }
    }
}
