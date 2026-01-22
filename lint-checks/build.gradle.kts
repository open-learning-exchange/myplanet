plugins {
    id("kotlin")
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.3.2")
    compileOnly("com.android.tools.lint:lint-checks:31.3.2")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
}

tasks.jar {
    manifest {
        attributes(
            "Lint-Registry-v2" to "org.ole.planet.myplanet.lint.MyIssueRegistry"
        )
    }
}
