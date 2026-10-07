package org.ole.planet.myplanet.services.sync

import android.app.Application
import com.google.gson.JsonArray as GsonJsonArray
import com.google.gson.JsonObject as GsonJsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import java.util.Date
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.AppNotification
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.relaxedInstance
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Goldens for the JSON [TransactionSyncManager] builds (the notification read-receipt it PUTs) and
 * for the documents it hands the repositories' sync writers from an `_all_docs` page. Pinned at
 * [ApiInterface] and at the writer arguments (Gson-typed until the repositories move), so they
 * hold whatever JSON types the manager uses in between.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SyncWireGoldenTest {

    private val api = mockk<ApiInterface>()

    /** One `_all_docs?include_docs=true` page: a design doc to skip, number literals, nulls, tricky text. */
    private val page = Json.parseToJsonElement(
        """{"total_rows":3,"rows":[
          {"id":"_design/x","doc":{"_id":"_design/x","views":{}}},
          {"id":"a","doc":{"_id":"a","_rev":"1-a","message":${Json.encodeToString(String.serializer(), TRICKY)},"n":1e2,"f":1.50,"big":$BIG_LONG,"neg":-0.0,"none":null,"list":[1,2.5,true,null]}},
          {"id":"b","value":{"rev":"2-b"},"doc":{"_id":"b","nested":{"k":[{"x":0.1}]}}}
        ]}"""
    ) as JsonObject

    @Before
    fun setUp() {
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "https://planet.example/db"
        every { UrlUtils.header } returns "Basic golden"
        coEvery { api.postDoc(any(), any(), any(), any()) } returns Response.success(page)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun manager(vararg overrides: Any): TransactionSyncManager =
        relaxedInstance<TransactionSyncManager>(api, TestDispatcherProvider(UnconfinedTestDispatcher()), *overrides)

    private fun List<GsonJsonObject>.asArray() = GsonJsonArray().also { array -> forEach { array.add(it) } }

    @Test
    fun `news docs handed to the sync writer`() = runTest {
        val voices = mockk<VoicesRepository>()
        val received = mutableListOf<List<JsonObject>>()
        coEvery { voices.insertNewsList(any()) } answers { received.add(firstArg()) }
        manager(voices).syncDb("news")
        assertEquals(1, received.size)
        assertMatchesGolden("services/sync/news-docs", JsonArray(received.single()))
    }

    @Test
    fun `tablet users rows handed to the sync writer`() = runTest {
        val users = mockk<UserSyncRepository>(relaxed = true)
        val received = mutableListOf<List<GsonJsonObject>>()
        coEvery { users.insertUsersFromSync(any()) } answers { received.add(firstArg()) }
        manager(users).syncDb("tablet_users")
        assertEquals(1, received.size)
        assertMatchesGolden("services/sync/tablet-users-rows", received.single().asArray())
    }

    @Test
    fun `course rows handed to the bulk writer`() = runTest {
        val courses = mockk<CoursesRepository>(relaxed = true)
        val received = mutableListOf<JsonArray>()
        coEvery { courses.bulkInsertFromSync(any()) } answers { received.add(firstArg()) }
        manager(courses).syncDb("courses")
        assertEquals(1, received.size)
        assertMatchesGolden("services/sync/courses-rows", received.single())
    }

    @Test
    fun `notification read receipt`() = runTest {
        val notifications = mockk<NotificationsRepository>(relaxed = true)
        coEvery { notifications.getPendingSyncNotifications() } returns listOf(
            AppNotification().apply {
                id = "n-1"; rev = "3-n"; userId = "org.couchdb.user:learner"; message = TRICKY; type = "survey"
                link = "https://planet.example/a?b=1&c=<2>"; priority = 2; createdAt = Date(BIG_LONG)
            },
            AppNotification().apply { id = "n-2"; rev = "1-n"; type = "task"; createdAt = Date(0L) },
        )
        val sent = mutableListOf<JsonObject>()
        coEvery { api.putDoc(any(), any(), any(), any()) } answers {
            sent.add(arg(3))
            Response.success(Json.parseToJsonElement("""{"ok":true,"rev":"4-n"}""") as JsonObject)
        }
        manager(notifications).syncNotificationReads()
        assertEquals(2, sent.size)
        assertMatchesGolden("services/sync/notification-read-full", sent[0])
        assertMatchesGolden("services/sync/notification-read-sparse", sent[1])
    }
}
