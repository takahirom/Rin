import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("org.jetbrains.compose")
    id("module.publication")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.core" && requested.name == "core-ktx") {
            useVersion("1.12.0")
        }
        if (requested.group == "androidx.compose.ui" && requested.name == "ui") {
            useVersion("1.7.0-alpha05")
        }
        if (requested.group == "androidx.compose.material" && requested.name == "material") {
            useVersion("1.7.0-alpha05")
        }
    }
}


kotlin {
    applyDefaultHierarchyTemplate()
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
//    linuxX64()
    val compose = extensions.get("compose") as org.jetbrains.compose.ComposeExtension
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.jetbrains.androidx.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.androidx.lifecycle.runtime.compose)
                implementation(compose.dependencies.runtime)
                implementation(compose.dependencies.ui)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val androidMain by getting {
            dependencies {
            }
        }
        @OptIn(ExperimentalComposeLibrary::class) val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.androidx.compose.ui.testing.junit)
                implementation(compose.dependencies.material3)
                implementation(compose.dependencies.material)
                implementation(libs.androidx.compose.integration.activity)
                implementation(libs.jetbrains.androidx.navigation.compose)
                implementation(libs.androidx.core)
                implementation(compose.dependencies.ui)
                implementation(compose.dependencies.uiTest)
                implementation(libs.truth)
                implementation(libs.leakcanary.android.instrumentation)
            }
        }
    }
}

android {
    namespace = "io.github.takahirom.rin"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
