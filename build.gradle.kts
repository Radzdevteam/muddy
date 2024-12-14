buildscript {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        flatDir {
            dirs("libs")
        }
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath(files("libs/muddy.jar"))
        classpath(files("libs/runtime.jar"))
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
