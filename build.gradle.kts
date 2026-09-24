import org.gradle.api.tasks.Delete

buildscript {
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath(libs.android.gradle.plugin)
        classpath(libs.hilt.android.gradle.plugin)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlin.serialization)
        classpath(libs.ksp.symbol.processing.gradle.plugin)
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
