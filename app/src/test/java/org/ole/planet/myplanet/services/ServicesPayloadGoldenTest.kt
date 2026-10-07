package org.ole.planet.myplanet.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.unmockkAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.di.NetworkModule
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.upload.VoicesUploader
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.callPrivate
import org.ole.planet.myplanet.utils.relaxedInstance
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Goldens for JSON the services persist (saved-users preference) or upload (news image resources). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ServicesPayloadGoldenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalContext: Context? = null

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        originalContext = try {
            MainApplication.context
        } catch (_: Exception) {
            null
        }
        MainApplication.testContext = context
    }

    @After
    fun tearDown() {
        MainApplication.testContext = originalContext
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        unmockkAll()
    }

    @Test
    fun `saved users preference`() {
        val manager = SharedPrefManager(context, NetworkModule.provideGson())
        manager.setSavedUsers(
            listOf(
                User(fullName = TRICKY, name = "learner", password = "p@ss=<word>&'", image = "https://x.org/a?b=1&c=2", source = "member"),
                User(name = "guest"),
            )
        )
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("savedUsers", null)
        assertMatchesRawGolden("services/savedUsers", stored.orEmpty())
    }

    @Test
    fun `news image resource document`() {
        val uploader = relaxedInstance<VoicesUploader>(TestTimeProvider(1_717_171_717_171L))
        val user = UserEntity(id = "org.couchdb.user:learner", parentCode = "parent", planetCode = "planet")
        val image = buildJsonObject { put("fileName", "photo <1> & 'x'.jpg") }
        assertMatchesGolden("services/voicesUploader-image-full", uploader.callPrivate("createImage", user, image))
        assertMatchesGolden("services/voicesUploader-image-sparse", uploader.callPrivate("createImage", null, null))
    }
}
