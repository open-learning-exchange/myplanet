package org.ole.planet.myplanet.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a baseline profile for myPlanet.
 *
 * Run on a connected device/emulator (API 28+, ideally a rooted/userdebug emulator):
 *
 *     ./gradlew :app:generateDefaultReleaseBaselineProfile
 *
 * This launches the app, exercises the critical startup path, and writes the profile to
 * `app/src/<flavor><BuildType>/generated/baselineProfiles/baseline-prof.txt`, which is then
 * bundled into the APK/AAB and AOT-compiled at install time via androidx.profileinstaller.
 *
 * Commit the generated `baseline-prof.txt` so every build ships it.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 15,
            stableIterations = 3,
        ) {
            // Critical user journey: cold start through the app's launcher activity until idle.
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    companion object {
        private const val PACKAGE_NAME = "org.ole.planet.myplanet"
    }
}
