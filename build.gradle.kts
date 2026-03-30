plugins {
    alias(libs.plugins.application) apply false
    alias(libs.plugins.legacy.kapt) apply false
    alias(libs.plugins.realm.android) apply false
    alias(libs.plugins.dagger.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    kotlin("android") version libs.versions.kotlin apply false
    kotlin("plugin.serialization") version libs.versions.kotlin apply false
}

import org.gradle.api.tasks.Delete

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
