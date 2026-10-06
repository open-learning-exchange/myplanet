package org.ole.planet.myplanet.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockkObject

/**
 * Representative inputs for the JSON golden tests: every string a Gson -> kotlinx switch could
 * encode differently (HTML-sensitive characters Gson escapes, quotes, backslashes, control
 * characters, U+2028, non-BMP unicode) and numbers at the edges of their lexical forms.
 */
object GoldenFixtures {
    const val TRICKY = "Tricky <b>&amp;</b> 'q' = \"dq\" \\ / — ünïcødé 漢字 🌍\n\tline\u2028sep"
    const val TRICKY_URL = "https://planet.example/a?b=1&c=<2>#'x'"

    /** 2^53 + 1: not representable as a double, so any double round-trip changes it. */
    const val BIG_LONG = 9_007_199_254_740_993L

    const val DEVICE_NAME = "golden-device"
    const val ANDROID_ID = "golden-android-id"
    const val CUSTOM_DEVICE_NAME = "golden <custom> & 'device'"

    /** Pins the device identity fields every upload document carries. Pair with `unmockkAll()`. */
    fun mockDeviceIdentity() {
        mockkObject(NetworkUtils)
        every { NetworkUtils.getDeviceName() } returns DEVICE_NAME
        every { NetworkUtils.getUniqueIdentifier() } returns ANDROID_ID
        every { NetworkUtils.getCustomDeviceName(any<Context>()) } returns CUSTOM_DEVICE_NAME
        mockkObject(VersionUtils)
        every { VersionUtils.getAndroidId(any()) } returns ANDROID_ID
    }
}
