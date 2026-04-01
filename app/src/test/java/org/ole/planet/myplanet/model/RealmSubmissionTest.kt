package org.ole.planet.myplanet.model

import android.content.Context
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
import java.util.UUID

class RealmSubmissionTest {

    @MockK
    lateinit var mockRealm: Realm

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockSharedPrefManager: SharedPrefManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(NetworkUtils::class)
        every { NetworkUtils.getUniqueIdentifier() } returns "mock-android-id"
        every { NetworkUtils.getDeviceName() } returns "mock-device-name"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "mock-custom-device-name"

        every { mockSharedPrefManager.getPlanetCode() } returns "mock-planet-code"
        every { mockSharedPrefManager.getParentCode() } returns "mock-parent-code"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSerialize() {
        // Arrange
        val submission = RealmSubmission().apply {
            _id = "sub-123"
            _rev = "rev-123"
            parentId = "exam-123@parent"
            type = "exam"
            grade = 95
            startTime = 1000L
            lastUpdateTime = 2000L
            status = "completed"
            sender = "sender-123"
            answers = RealmList()
            parent = "{\"id\":\"parent-json-123\"}"
            user = "{\"_id\":\"user-json-123\"}"
        }

        val mockExamQuery = mockk<RealmQuery<RealmStepExam>>()
        every { mockRealm.where(RealmStepExam::class.java) } returns mockExamQuery
        every { mockExamQuery.equalTo("id", "exam-123") } returns mockExamQuery
        every { mockExamQuery.findFirst() } returns null

        // Act
        val json = RealmSubmission.serialize(mockRealm, submission, mockContext, mockSharedPrefManager)

        // Assert
        assertEquals("sub-123", json.get("_id").asString)
        assertEquals("rev-123", json.get("_rev").asString)
        assertEquals("exam-123@parent", json.get("parentId").asString)
        assertEquals("exam", json.get("type").asString)
        assertEquals(95L, json.get("grade").asLong)
        assertEquals(1000L, json.get("startTime").asLong)
        assertEquals(2000L, json.get("lastUpdateTime").asLong)
        assertEquals("completed", json.get("status").asString)
        assertEquals("sender-123", json.get("sender").asString)
        assertEquals("mock-android-id", json.get("androidId").asString)
        assertEquals("mock-device-name", json.get("deviceName").asString)
        assertEquals("mock-custom-device-name", json.get("customDeviceName").asString)
        assertEquals("mock-planet-code", json.get("source").asString)
        assertEquals("mock-parent-code", json.get("parentCode").asString)
        assertEquals("parent-json-123", json.getAsJsonObject("parent").get("id").asString)
        assertEquals("user-json-123", json.getAsJsonObject("user").get("_id").asString)
    }

    @Test
    fun testSerializeExamResult() {
        // Arrange
        val submission = RealmSubmission().apply {
            _id = "sub-123"
            _rev = "rev-123"
            parentId = "exam-123@parent"
            type = "exam"
            grade = 95
            startTime = 1000L
            lastUpdateTime = 2000L
            status = "completed"
            sender = "sender-123"
            answers = RealmList()
            parent = "{\"id\":\"parent-json-123\"}"
            user = "{\"_id\":\"user-json-123\"}"
            userId = "user-123"
            teamObject = RealmTeamReference().apply {
                _id = "team-123"
                name = "Team A"
                type = "sync"
            }
        }

        val mockUserQuery = mockk<RealmQuery<RealmUser>>()
        every { mockRealm.where(RealmUser::class.java) } returns mockUserQuery
        every { mockUserQuery.equalTo("id", "user-123") } returns mockUserQuery
        val mockUser = mockk<RealmUser>()
        every { mockUser.serialize() } returns JsonObject().apply { addProperty("_id", "user-123") }
        every { mockUserQuery.findFirst() } returns mockUser

        val mockExamQuery = mockk<RealmQuery<RealmStepExam>>()
        every { mockRealm.where(RealmStepExam::class.java) } returns mockExamQuery
        every { mockExamQuery.equalTo("id", "exam-123") } returns mockExamQuery
        every { mockExamQuery.findFirst() } returns null

        // Act
        val json = RealmSubmission.serializeExamResult(mockRealm, submission, mockContext, mockSharedPrefManager)

        // Assert
        assertEquals("sub-123", json.get("_id").asString)
        assertEquals("rev-123", json.get("_rev").asString)
        assertEquals("exam-123@parent", json.get("parentId").asString)
        assertEquals("exam", json.get("type").asString)
        assertEquals(95L, json.get("grade").asLong)
        assertEquals(1000L, json.get("startTime").asLong)
        assertEquals(2000L, json.get("lastUpdateTime").asLong)
        assertEquals("completed", json.get("status").asString)
        assertEquals("sender-123", json.get("sender").asString)
        assertEquals("mock-android-id", json.get("androidId").asString)
        assertEquals("mock-device-name", json.get("deviceName").asString)
        assertEquals("mock-custom-device-name", json.get("customDeviceName").asString)
        assertEquals("mock-planet-code", json.get("source").asString)
        assertEquals("mock-parent-code", json.get("parentCode").asString)
        assertEquals("team-123", json.getAsJsonObject("team").get("_id").asString)
        assertEquals("Team A", json.getAsJsonObject("team").get("name").asString)
        assertEquals("sync", json.getAsJsonObject("team").get("type").asString)
        assertEquals("parent-json-123", json.getAsJsonObject("parent").get("id").asString)
        assertEquals("user-json-123", json.getAsJsonObject("user").get("_id").asString)
    }

    @Test
    fun testInsert() {
        // Mocking static methods is tricky in insert function as it heavily relies on mRealm.createObject
        // Let's test the basic flow

        val submissionJson = JsonObject().apply {
            addProperty("_id", "sub-123")
            addProperty("_rev", "rev-123")
            addProperty("status", "pending")
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "sub-123") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val mockSubmission = mockk<RealmSubmission>(relaxed = true)
        every { mockRealm.createObject(RealmSubmission::class.java, "sub-123") } returns mockSubmission

        every { mockRealm.isInTransaction } returns false
        every { mockRealm.beginTransaction() } just Runs
        every { mockRealm.commitTransaction() } just Runs

        // Act
        RealmSubmission.insert(mockRealm, submissionJson)

        // Assert
        verify { mockRealm.beginTransaction() }
        verify { mockRealm.createObject(RealmSubmission::class.java, "sub-123") }
        verify { mockSubmission._id = "sub-123" }
        verify { mockRealm.commitTransaction() }
    }
}
