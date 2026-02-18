# Foojay Toolchain Resolver

This document explains the changes introduced to the Gradle build infrastructure, specifically the adoption of the **Foojay Toolchain Resolver Plugin**.

## What is it?

The **Foojay Toolchain Resolver** is a Gradle plugin (applied in `settings.gradle` as `org.gradle.toolchains.foojay-resolver-convention`) that connects your build process to the [Foojay Discovery API](https://foojay.io/). It acts as a bridge, allowing Gradle to automatically find and download Java Development Kits (JDKs) that match your project's requirements.

## Why do we need it?

### 1. Automatic Provisioning (Convenience)
Without this plugin, every developer (and every CI server) needs to manually download and install the correct version of Java and ensure environment variables like `JAVA_HOME` are set correctly. This can be error-prone and tedious, especially for new contributors.

With the Foojay resolver, if the required Java version isn't found on the machine, Gradle automatically downloads it for you during the build process.

### 2. Reproducibility (Consistency)
It ensures that everyone building the project is using the **exact same** Java version. This eliminates "works on my machine" issues caused by minor version differences or different vendors (e.g., Oracle vs. Azul vs. JetBrains).

### 3. Decoupling
It separates the build configuration from the local machine's environment. You don't need to configure your system-wide Java version to match the project's needs; the project manages its own tools.

## Configuration Details

The following changes were made to support this:

1.  **Plugin Application**:
    In `settings.gradle`:
    ```groovy
    plugins {
        id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
    }
    ```

2.  **Daemon JVM Configuration**:
    A new file `gradle/gradle-daemon-jvm.properties` was added to specify the exact JVM version for the Gradle Daemon:
    ```properties
    toolchainVendor=JETBRAINS
    toolchainVersion=21
    ```
    This ensures the build tool itself runs on **Java 21**.

3.  **Application Compatibility**:
    The application code (`app/build.gradle`) continues to target **Java 17** for compatibility with Android devices:
    ```groovy
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    ```
