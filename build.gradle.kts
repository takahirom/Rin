plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    id("com.vanniktech.maven.publish") version "0.31.0" apply false
    id("org.jetbrains.compose") version "1.6.10" apply false
}

allprojects {
    group = "io.github.takahirom.rin"
    version = "0.3.0"
}
