package org.ole.planet.myplanet.model

import android.content.Context
import android.text.TextUtils
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils

class RealmTeamLogTest {

    @Before
    fun setup() {
        mockkObject(NetworkUtils)
        mockkStatic(TextUtils::class)
        mockkStatic(JsonUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = arg<CharSequence?>(0)
            str == null || str.length == 0
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetLastVisit() {
        val mockRealm = mockk<Realm>()
        val mockQuery = mockk<RealmQuery<RealmTeamLog>>()

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", "teamVisit") } returns mockQuery
        every { mockQuery.equalTo("user", "testUser") } returns mockQuery
        every { mockQuery.equalTo("teamId", "testTeam") } returns mockQuery
        every { mockQuery.max("time") } returns 123456789L

        val result = RealmTeamLog.getLastVisit(mockRealm, "testUser", "testTeam")

        assertEquals(123456789L, result)
    }

    @Test
    fun testGetLastVisit_returnsNull() {
        val mockRealm = mockk<Realm>()
        val mockQuery = mockk<RealmQuery<RealmTeamLog>>()

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", "teamVisit") } returns mockQuery
        every { mockQuery.equalTo("user", "testUser") } returns mockQuery
        every { mockQuery.equalTo("teamId", "testTeam") } returns mockQuery
        every { mockQuery.max("time") } returns null

        val result = RealmTeamLog.getLastVisit(mockRealm, "testUser", "testTeam")

        assertNull(result)
    }

    @Test
    fun testSerializeTeamActivities() {
        val mockContext = mockk<Context>()
        val log = RealmTeamLog().apply {
            user = "testUser"
            type = "teamVisit"
            createdOn = "2023-01-01"
            parentCode = "parent123"
            teamType = "testType"
            time = 123456789L
            teamId = "testTeam"
            _rev = "rev123"
            _id = "id123"
        }

        every { NetworkUtils.getUniqueIdentifier() } returns "androidId123"
        every { NetworkUtils.getDeviceName() } returns "deviceName123"
        every { NetworkUtils.getCustomDeviceName(mockContext) } returns "customName123"

        val jsonObject = RealmTeamLog.serializeTeamActivities(log, mockContext)

        assertEquals("testUser", jsonObject.get("user").asString)
        assertEquals("teamVisit", jsonObject.get("type").asString)
        assertEquals("2023-01-01", jsonObject.get("createdOn").asString)
        assertEquals("parent123", jsonObject.get("parentCode").asString)
        assertEquals("testType", jsonObject.get("teamType").asString)
        assertEquals(123456789L, jsonObject.get("time").asLong)
        assertEquals("testTeam", jsonObject.get("teamId").asString)
        assertEquals("androidId123", jsonObject.get("androidId").asString)
        assertEquals("deviceName123", jsonObject.get("deviceName").asString)
        assertEquals("customName123", jsonObject.get("customDeviceName").asString)
        assertEquals("rev123", jsonObject.get("_rev").asString)
        assertEquals("id123", jsonObject.get("_id").asString)
    }

    @Test
    fun testSerializeTeamActivities_noRev() {
        val mockContext = mockk<Context>()
        val log = RealmTeamLog().apply {
            user = "testUser"
            type = "teamVisit"
            createdOn = "2023-01-01"
            parentCode = "parent123"
            teamType = "testType"
            time = 123456789L
            teamId = "testTeam"
            _rev = ""
            _id = "id123"
        }

        every { NetworkUtils.getUniqueIdentifier() } returns "androidId123"
        every { NetworkUtils.getDeviceName() } returns "deviceName123"
        every { NetworkUtils.getCustomDeviceName(mockContext) } returns "customName123"

        val jsonObject = RealmTeamLog.serializeTeamActivities(log, mockContext)

        assertEquals("testUser", jsonObject.get("user").asString)
        assertEquals("teamVisit", jsonObject.get("type").asString)
        assertEquals("2023-01-01", jsonObject.get("createdOn").asString)
        assertEquals("parent123", jsonObject.get("parentCode").asString)
        assertEquals("testType", jsonObject.get("teamType").asString)
        assertEquals(123456789L, jsonObject.get("time").asLong)
        assertEquals("testTeam", jsonObject.get("teamId").asString)
        assertEquals("androidId123", jsonObject.get("androidId").asString)
        assertEquals("deviceName123", jsonObject.get("deviceName").asString)
        assertEquals("customName123", jsonObject.get("customDeviceName").asString)
        assertFalse(jsonObject.has("_rev"))
        assertFalse(jsonObject.has("_id"))
    }

    @Test
    fun testInsert_existingLog() {
        val mockRealm = mockk<Realm>()
        val mockQuery = mockk<RealmQuery<RealmTeamLog>>()
        val existingLog = mockk<RealmTeamLog>(relaxed = true)
        val jsonObject = mockk<JsonObject>()

        every { JsonUtils.getString("_id", jsonObject) } returns "id123"
        every { JsonUtils.getString("_rev", jsonObject) } returns "rev123"
        every { JsonUtils.getString("type", jsonObject) } returns "teamVisit"
        every { JsonUtils.getString("user", jsonObject) } returns "testUser"
        every { JsonUtils.getString("createdOn", jsonObject) } returns "2023-01-01"
        every { JsonUtils.getString("parentCode", jsonObject) } returns "parent123"
        every { JsonUtils.getLong("time", jsonObject) } returns 123456789L
        every { JsonUtils.getString("teamId", jsonObject) } returns "testTeam"
        every { JsonUtils.getString("teamType", jsonObject) } returns "testType"

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "id123") } returns mockQuery
        every { mockQuery.findFirst() } returns existingLog

        RealmTeamLog.insert(mockRealm, jsonObject)

        verify { existingLog._rev = "rev123" }
        verify { existingLog._id = "id123" }
        verify { existingLog.type = "teamVisit" }
        verify { existingLog.user = "testUser" }
        verify { existingLog.createdOn = "2023-01-01" }
        verify { existingLog.parentCode = "parent123" }
        verify { existingLog.time = 123456789L }
        verify { existingLog.teamId = "testTeam" }
        verify { existingLog.teamType = "testType" }
        verify(exactly = 0) { mockRealm.createObject(RealmTeamLog::class.java, any<String>()) }
    }

    @Test
    fun testInsert_newLog() {
        val mockRealm = mockk<Realm>()
        val mockQuery = mockk<RealmQuery<RealmTeamLog>>()
        val newLog = mockk<RealmTeamLog>(relaxed = true)
        val jsonObject = mockk<JsonObject>()

        every { JsonUtils.getString("_id", jsonObject) } returns "id123"
        every { JsonUtils.getString("_rev", jsonObject) } returns "rev123"
        every { JsonUtils.getString("type", jsonObject) } returns "teamVisit"
        every { JsonUtils.getString("user", jsonObject) } returns "testUser"
        every { JsonUtils.getString("createdOn", jsonObject) } returns "2023-01-01"
        every { JsonUtils.getString("parentCode", jsonObject) } returns "parent123"
        every { JsonUtils.getLong("time", jsonObject) } returns 123456789L
        every { JsonUtils.getString("teamId", jsonObject) } returns "testTeam"
        every { JsonUtils.getString("teamType", jsonObject) } returns "testType"

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "id123") } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmTeamLog::class.java, "id123") } returns newLog

        RealmTeamLog.insert(mockRealm, jsonObject)

        verify { newLog._rev = "rev123" }
        verify { newLog._id = "id123" }
        verify { newLog.type = "teamVisit" }
        verify { newLog.user = "testUser" }
        verify { newLog.createdOn = "2023-01-01" }
        verify { newLog.parentCode = "parent123" }
        verify { newLog.time = 123456789L }
        verify { newLog.teamId = "testTeam" }
        verify { newLog.teamType = "testType" }
    }
}
