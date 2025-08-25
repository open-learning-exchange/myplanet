import org.gradle.api.tasks.Delete

buildscript {
    val extra = project.extra
    extra["kotlin_version"] = "2.2.10"
    extra["setup"] = mapOf(
        "compileSdk" to 36,
        "buildTools" to "36.0.0",
        "minSdk" to 21,
        "targetSdk" to 36
    )
    extra["versions"] = mapOf(
        "supportLib" to "28.0.0"
    )

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        val kotlinVersion = extra["kotlin_version"] as String
        classpath("com.android.tools.build:gradle:8.12.1")
        classpath("io.realm:realm-gradle-plugin:10.19.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.57.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        // NOTE: Do not place your application dependencies here
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
