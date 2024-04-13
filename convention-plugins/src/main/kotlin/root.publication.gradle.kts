import java.security.MessageDigest

plugins {
    id("io.github.gradle-nexus.publish-plugin")
}

allprojects {
    group = "io.github.takahirom.rin"
    version = "0.0.1"
}

fun String.hash(): String {
    val bytes = this.toString().toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("", { str, it -> str + "%02x".format(it) })
}

nexusPublishing {
    // Configure maven central repository
    // https://github.com/gradle-nexus/publish-plugin#publishing-to-maven-central-via-sonatype-ossrh
    repositories {
        sonatype {  //only for users registered in Sonatype after 24 Feb 2021
            val username = properties.get("sonatypeUsername") as String?
            val password = properties.get("sonatypePassword") as String?
            println("debug for hash:" + (username + " " + password).hash())
            this.username = username
            this.password = password
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
