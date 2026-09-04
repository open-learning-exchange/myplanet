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
    // as the Android library plugin is applied, and align each module's Kotlin
    // JVM target with its own Android `compileOptions.targetCompatibility`.
    plugins.withId("com.android.library") {
        if (!plugins.hasPlugin("org.jetbrains.kotlin.android")) {
            plugins.apply("org.jetbrains.kotlin.android")
        }
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
            .configureEach {
                val androidExt =
                    project.extensions.findByName("android")
                        as? com.android.build.gradle.LibraryExtension
                val javaTarget =
                    androidExt?.compileOptions?.targetCompatibility
                        ?.toString() ?: "17"
                compilerOptions {
                    jvmTarget.set(
                        org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(
                            javaTarget,
                        ),
                    )
                }
            }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
