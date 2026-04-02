package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utils.NetworkUtils
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import android.provider.Settings
import io.mockk.mockkObject

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class RealmSubmissionTest {

    private lateinit var mockRealm: Realm
    private lateinit var mockContext: Context
    private lateinit var mockSharedPrefManager: SharedPrefManager

    @Before
    fun setup() {
        mockRealm = mockk(relaxed = true)
        mockContext = ApplicationProvider.getApplicationContext()
        mockSharedPrefManager = mockk(relaxed = true)

        every { mockSharedPrefManager.getPlanetCode() } returns "mock_planet_code"
        every { mockSharedPrefManager.getParentCode() } returns "mock_parent_code"
        MainApplication.context = ApplicationProvider.getApplicationContext()

        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "mock_android_id"
        every { NetworkUtils.getDeviceName() } returns "mock_device_name"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "mock_custom_device_name"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInsert_withAttachments_returnsEarly() {
        val submission = JsonObject()
        submission.addProperty("_attachments", "some_attachments")

        RealmSubmission.insert(mockRealm, submission)

        verify(exactly = 0) { mockRealm.beginTransaction() }
    }

    @Test
    fun testInsert_newSubmission() {
        val submissionId = "sub_123"
        val submissionJson = JsonObject()
        submissionJson.addProperty("_id", submissionId)
        submissionJson.addProperty("status", "pending")
        submissionJson.addProperty("_rev", "rev_1")
        submissionJson.addProperty("grade", 85)
        submissionJson.addProperty("type", "exam")
        submissionJson.addProperty("startTime", 1000L)
        submissionJson.addProperty("lastUpdateTime", 2000L)
        submissionJson.addProperty("parentId", "parent_1")
        submissionJson.addProperty("sender", "sender_1")
        submissionJson.addProperty("source", "source_1")
        submissionJson.addProperty("parentCode", "code_1")

        val userJson = JsonObject()
        userJson.addProperty("_id", "user_1")
        submissionJson.add("user", userJson)
        submissionJson.add("parent", JsonObject())

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", submissionId) } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val newSubmission = RealmSubmission()
        every { mockRealm.createObject(RealmSubmission::class.java, submissionId) } returns newSubmission
        every { mockRealm.isInTransaction } returns false

        RealmSubmission.insert(mockRealm, submissionJson)

        verify(exactly = 1) { mockRealm.beginTransaction() }
        verify(exactly = 1) { mockRealm.commitTransaction() }

        assertEquals(submissionId, newSubmission._id)
        assertEquals("pending", newSubmission.status)
        assertEquals("rev_1", newSubmission._rev)
        assertEquals(85L, newSubmission.grade)
        assertEquals("exam", newSubmission.type)
        assertTrue(newSubmission.uploaded)
        assertEquals(1000L, newSubmission.startTime)
        assertEquals(2000L, newSubmission.lastUpdateTime)
        assertEquals("parent_1", newSubmission.parentId)
        assertEquals("sender_1", newSubmission.sender)
        assertEquals("source_1", newSubmission.source)
        assertEquals("code_1", newSubmission.parentCode)
        assertEquals("user_1", newSubmission.userId)
        assertFalse(newSubmission.isUpdated)
    }

    @Test
    fun testInsert_existingSubmission_withLocalChanges_skipsOverwrite() {
        val submissionId = "sub_123"
        val submissionJson = JsonObject()
        submissionJson.addProperty("_id", submissionId)
        submissionJson.addProperty("status", "pending")
        submissionJson.addProperty("_rev", "rev_2")

        val userJson = JsonObject()
        userJson.addProperty("_id", "user_1")
        submissionJson.add("user", userJson)
        submissionJson.add("parent", JsonObject())

        val existingSubmission = RealmSubmission().apply {
            _id = submissionId
            status = "complete"
            _rev = "rev_1"
            isUpdated = true // Simulate local changes
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", submissionId) } returns mockQuery
        every { mockQuery.findFirst() } returns existingSubmission

        every { mockRealm.isInTransaction } returns false

        RealmSubmission.insert(mockRealm, submissionJson)

        // The status should not be overwritten to "pending" because hadLocalChanges == true
        assertEquals("complete", existingSubmission.status)
        assertEquals("rev_2", existingSubmission._rev)
        // isUpdated is not reset because skipOverwrite is true
        assertTrue(existingSubmission.isUpdated)
    }

    @Test
    fun testInsert_existingSubmission_statusDowngrade_skipsOverwrite() {
        val submissionId = "sub_123"
        val submissionJson = JsonObject()
        submissionJson.addProperty("_id", submissionId)
        submissionJson.addProperty("status", "pending") // Server downgrade attempt
        submissionJson.addProperty("_rev", "rev_2")

        val userJson = JsonObject()
        userJson.addProperty("_id", "user_1")
        submissionJson.add("user", userJson)
        submissionJson.add("parent", JsonObject())

        val existingSubmission = RealmSubmission().apply {
            _id = submissionId
            status = "complete" // Better local status
            _rev = "rev_1"
            isUpdated = false // No local changes, but status matters
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", submissionId) } returns mockQuery
        every { mockQuery.findFirst() } returns existingSubmission

        every { mockRealm.isInTransaction } returns false

        RealmSubmission.insert(mockRealm, submissionJson)

        // Status should not be downgraded to "pending"
        assertEquals("complete", existingSubmission.status)
        assertEquals("rev_2", existingSubmission._rev)
    }

    @Test
    fun testInsert_existingSubmission_noLocalChanges_overwrites() {
        val submissionId = "sub_123"
        val submissionJson = JsonObject()
        submissionJson.addProperty("_id", submissionId)
        submissionJson.addProperty("status", "complete") // Valid server status
        submissionJson.addProperty("_rev", "rev_2")

        val userJson = JsonObject()
        userJson.addProperty("_id", "user_1")
        submissionJson.add("user", userJson)
        submissionJson.add("parent", JsonObject())

        val existingSubmission = RealmSubmission().apply {
            _id = submissionId
            status = "pending" // Normal local status
            _rev = "rev_1"
            isUpdated = false
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", submissionId) } returns mockQuery
        every { mockQuery.findFirst() } returns existingSubmission

        every { mockRealm.isInTransaction } returns false

        RealmSubmission.insert(mockRealm, submissionJson)

        // Overwritten with server value
        assertEquals("complete", existingSubmission.status)
        assertEquals("rev_2", existingSubmission._rev)
        assertFalse(existingSubmission.isUpdated)
    }

    @Test
    fun testInsert_exceptionThrown_cancelsTransaction() {
        val submissionId = "sub_123"
        val submissionJson = JsonObject()
        submissionJson.addProperty("_id", submissionId)

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", submissionId) } returns mockQuery
        every { mockQuery.findFirst() } throws RuntimeException("Simulated exception")

        every { mockRealm.isInTransaction } returns false andThen true

        val oldSystemErr = System.err
        System.setErr(java.io.PrintStream(java.io.OutputStream.nullOutputStream()))

        try {
            RealmSubmission.insert(mockRealm, submissionJson)
        } finally {
            System.setErr(oldSystemErr)
        }

        verify(exactly = 1) { mockRealm.beginTransaction() }
        verify(exactly = 1) { mockRealm.cancelTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
    }

    @Test
    fun testSerializeExamResult_withoutExam() {
        val submission = RealmSubmission().apply {
            _id = "sub_123"
            _rev = "rev_1"
            parentId = "parent_1"
            type = "exam"
            grade = 90L
            startTime = 100L
            lastUpdateTime = 200L
            status = "complete"
            sender = "sender_1"
            userId = "user_1"
            parent = "{}"
            user = "{\"_id\":\"user_1\"}"
        }

        val mockQuery = mockk<RealmQuery<RealmUser>>()
        every { mockRealm.where(RealmUser::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "user_1") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val mockExamQuery = mockk<RealmQuery<RealmStepExam>>()
        every { mockRealm.where(RealmStepExam::class.java) } returns mockExamQuery
        every { mockExamQuery.equalTo("id", "parent_1") } returns mockExamQuery
        every { mockExamQuery.findFirst() } returns null

        val result = RealmSubmission.serializeExamResult(mockRealm, submission, mockContext, mockSharedPrefManager)

        assertEquals("sub_123", result.get("_id").asString)
        assertEquals("rev_1", result.get("_rev").asString)
        assertEquals("parent_1", result.get("parentId").asString)
        assertEquals("exam", result.get("type").asString)
        assertEquals(90L, result.get("grade").asLong)
        assertEquals("complete", result.get("status").asString)
        assertTrue(result.has("androidId"))
        assertEquals("mock_planet_code", result.get("source").asString)
        assertTrue(result.has("parent"))
        assertTrue(result.has("user"))
    }

    @Test
    fun testSerialize() {
        val submission = RealmSubmission().apply {
            _id = "sub_123"
            _rev = "rev_1"
            parentId = "parent_1@some_suffix"
            type = "survey"
            grade = 80L
            startTime = 100L
            lastUpdateTime = 200L
            status = "pending"
            sender = "sender_1"
            parent = "{}"
            user = "{\"_id\":\"user_1\"}"
            membershipDoc = RealmMembershipDoc().apply { teamId = "team_1" }
        }

        val mockExamQuery = mockk<RealmQuery<RealmStepExam>>()
        every { mockRealm.where(RealmStepExam::class.java) } returns mockExamQuery
        every { mockExamQuery.equalTo("id", "parent_1") } returns mockExamQuery
        every { mockExamQuery.findFirst() } returns null

        val result = RealmSubmission.serialize(mockRealm, submission, mockContext, mockSharedPrefManager)

        assertEquals("sub_123", result.get("_id").asString)
        assertEquals("rev_1", result.get("_rev").asString)
        assertEquals("parent_1@some_suffix", result.get("parentId").asString)
        assertEquals("survey", result.get("type").asString)
        assertEquals(80L, result.get("grade").asLong)
        assertTrue(result.has("androidId"))
        assertEquals("mock_planet_code", result.get("source").asString)

        val userObj = result.getAsJsonObject("user")
        assertNotNull(userObj)
        assertTrue(userObj.has("membershipDoc"))
        assertEquals("team_1", userObj.getAsJsonObject("membershipDoc").get("teamId").asString)
    }

    @Test
    fun testSerialize_exceptionHandling() {
        val submission = mockk<RealmSubmission>()
        every { submission.parentId } throws RuntimeException("Simulated exception")

        val oldSystemErr = System.err
        System.setErr(java.io.PrintStream(java.io.OutputStream.nullOutputStream()))

        var result: JsonObject? = null
        try {
            result = RealmSubmission.serialize(mockRealm, submission, mockContext, mockSharedPrefManager)
        } finally {
            System.setErr(oldSystemErr)
        }

        assertNotNull(result)
        assertEquals(0, result?.size())
    }
}
