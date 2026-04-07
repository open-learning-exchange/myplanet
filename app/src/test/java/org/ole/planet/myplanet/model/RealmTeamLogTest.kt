package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils

class RealmTeamLogTest {

    private lateinit var mockRealm: Realm
    private lateinit var mockContext: Context
    private lateinit var mockQuery: RealmQuery<RealmTeamLog>

    @Before
    fun setup() {
        mockRealm = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockQuery = mockk(relaxed = true)

        mockkObject(NetworkUtils)
        mockkStatic(JsonUtils::class)
        org.ole.planet.myplanet.MainApplication.context = mockContext

        every { NetworkUtils.getUniqueIdentifier() } returns "mockAndroidId"
        every { NetworkUtils.getDeviceName() } returns "mockDeviceName"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "mockCustomDeviceName"

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val str = it.invocation.args[0] as? CharSequence
            str == null || str.length == 0
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getLastVisit returns correct max time when log exists`() {
        // Arrange
        val userName = "testUser"
        val teamId = "testTeam"
        val expectedTime = 1622548800000L

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", "teamVisit") } returns mockQuery
        every { mockQuery.equalTo("user", userName) } returns mockQuery
        every { mockQuery.equalTo("teamId", teamId) } returns mockQuery
        every { mockQuery.max("time") } returns expectedTime

        // Act
        val actualTime = RealmTeamLog.getLastVisit(mockRealm, userName, teamId)

        // Assert
        assertEquals(expectedTime, actualTime)
        verify(exactly = 1) { mockRealm.where(RealmTeamLog::class.java) }
        verify(exactly = 1) { mockQuery.max("time") }
    }

    @Test
    fun `getLastVisit returns null when no log exists`() {
        // Arrange
        val userName = "testUser"
        val teamId = "testTeam"

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", "teamVisit") } returns mockQuery
        every { mockQuery.equalTo("user", userName) } returns mockQuery
        every { mockQuery.equalTo("teamId", teamId) } returns mockQuery
        every { mockQuery.max("time") } returns null

        // Act
        val actualTime = RealmTeamLog.getLastVisit(mockRealm, userName, teamId)

        // Assert
        assertEquals(null, actualTime)
    }

    @Test
    fun `serializeTeamActivities populates JSON object correctly without _rev`() {
        // Arrange
        val log = RealmTeamLog().apply {
            user = "testUser"
            type = "teamVisit"
            createdOn = "2023-01-01"
            parentCode = "parent123"
            teamType = "local"
            time = 123456789L
            teamId = "team123"
        }

        // Act
        val jsonObject = RealmTeamLog.serializeTeamActivities(log, mockContext)

        // Assert
        assertEquals("testUser", jsonObject.get("user").asString)
        assertEquals("teamVisit", jsonObject.get("type").asString)
        assertEquals("2023-01-01", jsonObject.get("createdOn").asString)
        assertEquals("parent123", jsonObject.get("parentCode").asString)
        assertEquals("local", jsonObject.get("teamType").asString)
        assertEquals(123456789L, jsonObject.get("time").asLong)
        assertEquals("team123", jsonObject.get("teamId").asString)
        assertEquals("mockAndroidId", jsonObject.get("androidId").asString)
        assertEquals("mockDeviceName", jsonObject.get("deviceName").asString)
        assertEquals("mockCustomDeviceName", jsonObject.get("customDeviceName").asString)
        assertEquals(false, jsonObject.has("_rev"))
        assertEquals(false, jsonObject.has("_id"))
    }

    @Test
    fun `serializeTeamActivities populates JSON object correctly with _rev`() {
        // Arrange
        val log = RealmTeamLog().apply {
            user = "testUser"
            _rev = "1-rev"
            _id = "doc123"
        }

        // Act
        val jsonObject = RealmTeamLog.serializeTeamActivities(log, mockContext)

        // Assert
        assertEquals("testUser", jsonObject.get("user").asString)
        assertEquals("1-rev", jsonObject.get("_rev").asString)
        assertEquals("doc123", jsonObject.get("_id").asString)
    }

    @Test
    fun `insert creates new log when not found`() {
        // Arrange
        val act = JsonObject()
        val mockLog = mockk<RealmTeamLog>(relaxed = true)

        every { JsonUtils.getString(any() as String, any() as JsonObject?) } returns "fallbackString"
        every { JsonUtils.getLong(any() as String, any() as JsonObject?) } returns 0L

        every { JsonUtils.getString("_id", act) } returns "newDoc123"
        every { JsonUtils.getString("_rev", act) } returns "1-rev"
        every { JsonUtils.getString("type", act) } returns "teamVisit"
        every { JsonUtils.getString("user", act) } returns "testUser"
        every { JsonUtils.getString("createdOn", act) } returns "2023-01-01"
        every { JsonUtils.getString("parentCode", act) } returns "parent123"
        every { JsonUtils.getLong("time", act) } returns 123456789L
        every { JsonUtils.getString("teamId", act) } returns "team123"
        every { JsonUtils.getString("teamType", act) } returns "local"

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "newDoc123") } returns mockQuery
        every { mockQuery.findFirst() } returns null

        every { mockRealm.createObject(RealmTeamLog::class.java, "newDoc123") } returns mockLog

        // Act
        RealmTeamLog.insert(mockRealm, act)

        // Assert
        verify(exactly = 1) { mockRealm.createObject(RealmTeamLog::class.java, "newDoc123") }
        verify { mockLog._rev = "1-rev" }
        verify { mockLog._id = "newDoc123" }
        verify { mockLog.type = "teamVisit" }
        verify { mockLog.user = "testUser" }
        verify { mockLog.createdOn = "2023-01-01" }
        verify { mockLog.parentCode = "parent123" }
        verify { mockLog.time = 123456789L }
        verify { mockLog.teamId = "team123" }
        verify { mockLog.teamType = "local" }
    }

    @Test
    fun `insert updates existing log when found`() {
        // Arrange
        val act = JsonObject()
        val mockLog = mockk<RealmTeamLog>(relaxed = true)

        every { JsonUtils.getString(any() as String, any() as JsonObject?) } returns "fallbackString"
        every { JsonUtils.getLong(any() as String, any() as JsonObject?) } returns 0L

        every { JsonUtils.getString("_id", act) } returns "existingDoc123"
        every { JsonUtils.getString("_rev", act) } returns "2-rev"
        every { JsonUtils.getString("type", act) } returns "updatedType"

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "existingDoc123") } returns mockQuery
        every { mockQuery.findFirst() } returns mockLog

        // Act
        RealmTeamLog.insert(mockRealm, act)

        // Assert
        verify(exactly = 0) { mockRealm.createObject(RealmTeamLog::class.java, any<String>()) }
        verify { mockLog._rev = "2-rev" }
        verify { mockLog._id = "existingDoc123" }
        verify { mockLog.type = "updatedType" }
    }
}
