allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

subprojects {
    // file_picker 11.0.x conditionally skips the Kotlin Android plugin when
    // AGP is 9+, but this project keeps `android.builtInKotlin=false` in
    // gradle.properties. Without either built-in Kotlin or KGP, the plugin's
    // .kt sources are never compiled and the app fails to find the class at
    // `:app:compileDebugJavaWithJavac`. Apply KGP to any library module as soon
    // as the Android library plugin is applied, and align Kotlin's JVM target
    // with the Java 17 compile options used by the rest of the build.
    plugins.withId("com.android.library") {
        if (!plugins.hasPlugin("org.jetbrains.kotlin.android")) {
            plugins.apply("org.jetbrains.kotlin.android")
        }
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
            .configureEach {
                compilerOptions {
                    jvmTarget.set(
                        org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17,
                    )
                }
            }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
