import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.`maven-publish`

plugins {
    `maven-publish`
    signing
}

publishing {
    // Configure all publications
    publications.withType<MavenPublication> {
        // Stub javadoc.jar artifact
        artifact(tasks.register("${name}JavadocJar", Jar::class) {
            archiveClassifier.set("javadoc")
            archiveAppendix.set(this@withType.name)
        })

        // Provide artifacts information required by Maven Central
        pom {
            name.set("Rin")
            description.set("This library enhances Compose Multiplatform by enabling the use of `rememberRetained{}`, which is stored within ViewModel. It broadens the versatility of Compose, allowing it to be utilized in a wider array of contexts and scenarios.")
            url.set("https://github.com/takahirom/Rin")

            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://opensource.org/licenses/Apache-2.0.html")
                }
            }
            developers {
                developer {
                    id.set("takahirom")
                    name.set("takahirom")
                }
            }
            scm {
                url.set("https://github.com/takahirom/Rin")
            }
        }
    }
}

signing {
    if (project.hasProperty("signing.gnupg.keyName")) {
        useGpgCmd()
        sign(publishing.publications)
    }
}
