package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.NetworkUtils
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q], application = android.app.Application::class)
class RealmSubmissionTest {

    @MockK
    lateinit var mockRealm: Realm

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockSpm: SharedPrefManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "android_id"
        every { NetworkUtils.getDeviceName() } returns "device_name"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom_device"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `insert with attachments returns immediately`() {
        val submission = JsonObject()
        submission.addProperty("_attachments", "some_value")

        RealmSubmission.insert(mockRealm, submission)

        verify(exactly = 0) { mockRealm.beginTransaction() }
    }

    @Test
    fun `insert new submission`() {
        val submission = JsonObject()
        submission.addProperty("_id", "sub123")
        submission.addProperty("_rev", "rev1")
        submission.addProperty("status", "pending")

        val userJson = JsonObject()
        userJson.addProperty("_id", "user123")
        submission.add("user", userJson)

        every { mockRealm.isInTransaction } returns false
        every { mockRealm.beginTransaction() } just Runs
        every { mockRealm.commitTransaction() } just Runs

        val mockQuery = mockk<RealmQuery<RealmSubmission>>(relaxed = true)
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "sub123") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val mockSub = mockk<RealmSubmission>(relaxed = true)
        every { mockRealm.createObject(RealmSubmission::class.java, "sub123") } returns mockSub

        RealmSubmission.insert(mockRealm, submission)

        verify { mockRealm.beginTransaction() }
        verify { mockRealm.createObject(RealmSubmission::class.java, "sub123") }
        verify { mockSub._id = "sub123" }
        verify { mockSub.status = "pending" }
        verify { mockRealm.commitTransaction() }
    }

    @Test
    fun `insert existing submission with exception cancels transaction`() {
        val submission = JsonObject()
        submission.addProperty("_id", "sub123")

        // Provide sequential answers for isInTransaction since it's checked twice (start and catch)
        every { mockRealm.isInTransaction } returnsMany listOf(false, true)
        every { mockRealm.beginTransaction() } just Runs
        every { mockRealm.cancelTransaction() } just Runs

        val mockQuery = mockk<RealmQuery<RealmSubmission>>(relaxed = true)
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "sub123") } returns mockQuery

        // Throw an exception when finding first to trigger catch block
        val silentException = object : RuntimeException("Test Exception") {
            override fun printStackTrace() {
                // Do nothing to keep test logs clean
            }
        }
        every { mockQuery.findFirst() } throws silentException

        RealmSubmission.insert(mockRealm, submission)

        verify { mockRealm.beginTransaction() }
        verify { mockRealm.cancelTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
    }

    @Test
    fun `serialize submission`() {
        val mockSub = mockk<RealmSubmission>(relaxed = true)
        every { mockSub._id } returns "sub123"
        every { mockSub._rev } returns "rev1"
        every { mockSub.parentId } returns "parent123"
        every { mockSub.type } returns "survey"
        every { mockSub.grade } returns 100L
        every { mockSub.startTime } returns 1000L
        every { mockSub.lastUpdateTime } returns 2000L
        every { mockSub.status } returns "complete"
        every { mockSub.sender } returns "sender1"
        every { mockSub.answers } returns null
        every { mockSub.parent } returns "{}"
        every { mockSub.user } returns "{}"
        every { mockSub.membershipDoc } returns null

        val mockQuery = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        every { mockRealm.where(RealmStepExam::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "parent123") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val result = RealmSubmission.serialize(mockRealm, mockSub, mockContext, "planet", "parentPlanet")

        assertEquals("sub123", result.get("_id").asString)
        assertEquals("rev1", result.get("_rev").asString)
        assertEquals("parent123", result.get("parentId").asString)
        assertEquals("survey", result.get("type").asString)
        assertEquals(100L, result.get("grade").asLong)
        assertEquals("complete", result.get("status").asString)
        assertEquals("android_id", result.get("androidId").asString)
        assertEquals("planet", result.get("source").asString)
        assertEquals("parentPlanet", result.get("parentCode").asString)
    }
}
