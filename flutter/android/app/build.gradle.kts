plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "org.ole.planet.myplanet"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // `flutter_local_notifications` compiles against `java.time`, which is
        // only available from API 26 in the platform itself. minSdk is 26, but
        // the plugin's AAR metadata demands desugaring unconditionally and the
        // build fails at `checkDebugAarMetadata` without it.
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "org.ole.planet.myplanet"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // The Kotlin app ships minSdk 26 (see ../app/build.gradle), and the
        // planet_platform_channels plugin uses API-26 StorageStatsManager, so
        // the port must not claim to support anything older than its sibling.
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        debug {
            // The port and the shipping Kotlin app share an applicationId, and
            // this build is signed with the debug keys, so installing it beside
            // the real myPlanet is *refused* on a signature mismatch rather
            // than replacing it — you would have to uninstall the app you are
            // trying to compare against. The suffix gives the debug build its
            // own package identity so both sit on one handset.
            //
            // Nothing reads the id statically: `namespace` (and so
            // `.MainActivity`, the R class and `MainActivity.kt`'s package) is
            // unaffected by a suffix, the manifest interpolates no
            // `${applicationId}`, and every runtime consumer asks the
            // platform — `PlanetPlatformChannelsPlugin` passes
            // `context.packageName` to `getPackageInfo` and to the
            // tablet-usage filter, which is then correctly the usage of *this*
            // app rather than its sibling's.
            //
            // Deliberately no `versionNameSuffix` to go with it.
            // `ConfigurationsRepository` compares the **runtime** versionName
            // against the server's `minapk` and reports anything unusable as
            // an unreachable server (Phase 60), and `compareVersions` tolerates
            // `-lite` specifically, not an arbitrary tag. A cosmetic suffix
            // there risks the first screen saying it cannot reach Planet.
            applicationIdSuffix = ".flutter"
        }
        release {
            // Left on the real applicationId on purpose: this build represents
            // the eventual in-place cutover, so forking its identity would
            // model something that is not going to happen. It therefore still
            // cannot be installed beside the Kotlin app — use the debug APK for
            // side-by-side work.

            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Required by `isCoreLibraryDesugaringEnabled` above.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

flutter {
    source = "../.."
}
