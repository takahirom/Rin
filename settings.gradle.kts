pluginManagement {
    includeBuild("convention-plugins")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()

        maven {
            url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
        }
        maven { url = uri("https://androidx.dev/snapshots/builds/11651447/artifacts/repository") }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://androidx.dev/snapshots/builds/11651447/artifacts/repository") }

        maven {
            url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
        }
    }
}

rootProject.name = "Rin"
include(":rin")
