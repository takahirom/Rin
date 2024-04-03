import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.`maven-publish`
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

plugins {
  id("org.jetbrains.compose")
  id("com.android.library")
}

android {
  buildFeatures.compose = true
  composeOptions {
    kotlinCompilerExtensionVersion = libs.version("composeCompiler")
  }
}
val compose = extensions.get("compose") as org.jetbrains.compose.ComposeExtension
kotlinExtension.apply {
  with(sourceSets) {
    getByName("commonMain").apply {
      dependencies {
        implementation(compose.dependencies.runtime)
        implementation(compose.dependencies.foundation)
        implementation(compose.dependencies.material3)
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.dependencies.components.resources)
        implementation(libs.library("androidxLifecycleViewModel"))
        implementation(libs.library("androidxLifecycleCommon"))
      }
    }
    getByName("androidMain").apply {
      dependencies {
        implementation(libs.library("androidxActivityActivityCompose"))
        implementation(libs.library("composeUiToolingPreview"))
      }
    }
  }
}
