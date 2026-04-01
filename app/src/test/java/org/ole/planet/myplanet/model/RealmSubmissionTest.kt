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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.NetworkUtils

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

        mockkObject(NetworkUtils)
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
            membershipDoc = RealmMembershipDoc().apply { teamId = "team-123" }
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
        assertEquals("team-123", json.getAsJsonObject("user").getAsJsonObject("membershipDoc").get("teamId").asString)
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

        val mockExam = mockk<RealmStepExam>()
        every { mockExamQuery.findFirst() } returns mockExam
        mockkStatic(RealmStepExam::class)
        every { RealmStepExam.serializeExam(mockRealm, mockExam) } returns JsonObject().apply { addProperty("id", "exam-123") }

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
        assertEquals("exam-123", json.getAsJsonObject("parent").get("id").asString)
        assertEquals("user-123", json.getAsJsonObject("user").get("_id").asString)
    }

    @Test
    fun testInsertWithAttachmentsReturnsEarly() {
        val json = JsonObject().apply { addProperty("_attachments", "some-attachment") }
        RealmSubmission.insert(mockRealm, json)
        verify(exactly = 0) { mockRealm.beginTransaction() }
    }

    @Test
    fun testInsertNewSubmission() {
        val submissionJson = JsonObject().apply {
            addProperty("_id", "sub-123")
            addProperty("_rev", "rev-123")
            addProperty("status", "complete")
            addProperty("grade", 100)
            addProperty("type", "survey")
            addProperty("startTime", 12345L)
            addProperty("lastUpdateTime", 67890L)
            addProperty("parentId", "parent-123@abc")
            addProperty("sender", "sender-xyz")
            addProperty("source", "source-xyz")
            addProperty("parentCode", "pc-123")
            add("parent", JsonObject().apply { addProperty("id", "p-id") })
            add("user", JsonObject().apply {
                addProperty("_id", "org.couchdb.user:testuser@server")
                add("membershipDoc", JsonObject().apply {
                    addProperty("teamId", "team-123")
                })
            })
            add("team", JsonObject().apply {
                addProperty("_id", "team-123")
                addProperty("name", "Team One")
                addProperty("type", "sync")
            })
            add("answers", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("value", "answer 1")
                    addProperty("mistakes", 0)
                    addProperty("passed", true)
                    addProperty("questionId", "q-1")
                })
            })
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "sub-123") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val mockSubmission = RealmSubmission()
        every { mockRealm.createObject(RealmSubmission::class.java, "sub-123") } returns mockSubmission

        val mockTeamRef = RealmTeamReference()
        every { mockRealm.createObject(RealmTeamReference::class.java) } returns mockTeamRef

        val mockMembershipDoc = RealmMembershipDoc()
        every { mockRealm.createObject(RealmMembershipDoc::class.java) } returns mockMembershipDoc

        every { mockRealm.isInTransaction } returns false
        every { mockRealm.beginTransaction() } just Runs
        every { mockRealm.commitTransaction() } just Runs
        every { mockRealm.copyToRealmOrUpdate(any<List<RealmAnswer>>()) } returns RealmList(RealmAnswer())

        // Act
        RealmSubmission.insert(mockRealm, submissionJson)

        // Assert
        verify { mockRealm.beginTransaction() }
        verify { mockRealm.createObject(RealmSubmission::class.java, "sub-123") }

        assertEquals("sub-123", mockSubmission._id)
        assertEquals("rev-123", mockSubmission._rev)
        assertEquals("complete", mockSubmission.status)
        assertEquals(false, mockSubmission.isUpdated)
        assertEquals(100L, mockSubmission.grade)
        assertEquals("survey", mockSubmission.type)
        assertEquals(true, mockSubmission.uploaded)
        assertEquals(12345L, mockSubmission.startTime)
        assertEquals(67890L, mockSubmission.lastUpdateTime)
        assertEquals("parent-123@abc", mockSubmission.parentId)
        assertEquals("sender-xyz", mockSubmission.sender)
        assertEquals("source-xyz", mockSubmission.source)
        assertEquals("pc-123", mockSubmission.parentCode)
        assertTrue(mockSubmission.parent!!.contains("p-id"))
        assertTrue(mockSubmission.user!!.contains("org.couchdb.user:testuser@server"))
        assertEquals("org.couchdb.user:testuser", mockSubmission.userId)

        assertEquals("team-123", mockTeamRef._id)
        assertEquals("Team One", mockTeamRef.name)
        assertEquals("sync", mockTeamRef.type)
        assertEquals(mockTeamRef, mockSubmission.teamObject)

        assertEquals("team-123", mockMembershipDoc.teamId)
        assertEquals(mockMembershipDoc, mockSubmission.membershipDoc)

        assertNotNull(mockSubmission.answers)
        verify { mockRealm.commitTransaction() }
    }

    @Test
    fun testInsertExistingSubmissionHadLocalChangesSkipsOverwrite() {
        val submissionJson = JsonObject().apply {
            addProperty("_id", "sub-123")
            addProperty("status", "complete")
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "sub-123") } returns mockQuery

        val existingSubmission = RealmSubmission().apply {
            _id = "sub-123"
            status = "pending"
            isUpdated = true // had local changes
        }
        every { mockQuery.findFirst() } returns existingSubmission

        every { mockRealm.isInTransaction } returns false
        every { mockRealm.beginTransaction() } just Runs
        every { mockRealm.commitTransaction() } just Runs
        every { mockRealm.copyToRealmOrUpdate(any<List<RealmAnswer>>()) } returns RealmList()

        // Act
        RealmSubmission.insert(mockRealm, submissionJson)

        // Assert
        assertEquals("pending", existingSubmission.status) // Status should not be updated
        assertEquals(true, existingSubmission.isUpdated) // isUpdated should not be changed
    }

    @Test
    fun testInsertExistingSubmissionStatusDowngradeSkipsOverwrite() {
        val submissionJson = JsonObject().apply {
            addProperty("_id", "sub-123")
            addProperty("status", "pending") // Server status is downgrade
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "sub-123") } returns mockQuery

        val existingSubmission = RealmSubmission().apply {
            _id = "sub-123"
            status = "complete" // Local status is complete
            isUpdated = false
        }
        every { mockQuery.findFirst() } returns existingSubmission

        every { mockRealm.isInTransaction } returns false
        every { mockRealm.beginTransaction() } just Runs
        every { mockRealm.commitTransaction() } just Runs
        every { mockRealm.copyToRealmOrUpdate(any<List<RealmAnswer>>()) } returns RealmList()

        // Act
        RealmSubmission.insert(mockRealm, submissionJson)

        // Assert
        assertEquals("complete", existingSubmission.status) // Status should not be downgraded
    }

    @Test
    fun testInsertWhenAlreadyInTransactionDoesNotBeginOrCommit() {
        val submissionJson = JsonObject().apply {
            addProperty("_id", "sub-123")
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "sub-123") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        val mockSubmission = RealmSubmission()
        every { mockRealm.createObject(RealmSubmission::class.java, "sub-123") } returns mockSubmission

        every { mockRealm.isInTransaction } returns true // Already in transaction

        // Act
        RealmSubmission.insert(mockRealm, submissionJson)

        // Assert
        verify(exactly = 0) { mockRealm.beginTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
    }

    @Test
    fun testInsertExceptionCancelsTransaction() {
        val submissionJson = JsonObject().apply {
            addProperty("_id", "sub-123")
        }

        class CustomException(msg: String) : RuntimeException(msg) {
            override fun printStackTrace() {
                // Do nothing to avoid polluting test logs
            }
        }

        val mockQuery = mockk<RealmQuery<RealmSubmission>>()
        every { mockRealm.where(RealmSubmission::class.java) } throws CustomException("Simulated exception")

        every { mockRealm.isInTransaction } returns false andThen true
        every { mockRealm.beginTransaction() } just Runs
        every { mockRealm.cancelTransaction() } just Runs

        // Act
        RealmSubmission.insert(mockRealm, submissionJson)

        // Assert
        verify { mockRealm.beginTransaction() }
        verify { mockRealm.cancelTransaction() }
        verify(exactly = 0) { mockRealm.commitTransaction() }
    }
}
