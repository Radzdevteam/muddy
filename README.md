# Project Setup Instructions

Follow the steps below to set up the project correctly:

## Step 1: Download and Add Libraries
1. Download the required libraries.
2. Place the two JAR files (`muddy.jar` and `runtime.jar`) into the `libs` directory of your project.

## Step 2: Update the Root `build.gradle` File

Add the following code to the root `build.gradle` file to configure the classpath dependencies:

```gradle
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
```

## Step 3: Update the Application Module's `build.gradle` File

Add the following code to your application module's `build.gradle` file:

```gradle
plugins {
    id("muddy-plugin")
}

muddy {
    includes = listOf("com.radzdev.muddytest")
}
```

## Notes
- Ensure that the `libs` directory exists in your project root and contains the required JAR files.
- Replace `com.radzdev.muddytest` in the `muddy` block with the appropriate package name for your project, if needed.
- Verify that your project is synced and builds successfully after making these changes.
