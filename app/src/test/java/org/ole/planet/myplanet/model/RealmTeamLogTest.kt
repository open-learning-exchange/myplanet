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
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils

class RealmTeamLogTest {

    @Before
    fun setup() {
        mockkObject(NetworkUtils)
        mockkStatic(JsonUtils::class)
        mockkStatic(TextUtils::class)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun testGetLastVisit() {
        val mockRealm = mockk<Realm>()
        val mockQuery = mockk<RealmQuery<RealmTeamLog>>()

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("type", "teamVisit") } returns mockQuery
        every { mockQuery.equalTo("user", "testUser") } returns mockQuery
        every { mockQuery.equalTo("teamId", "testTeamId") } returns mockQuery
        every { mockQuery.max("time") } returns 123456789L

        val result = RealmTeamLog.getLastVisit(mockRealm, "testUser", "testTeamId")
        assertEquals(123456789L, result)

        verify { mockRealm.where(RealmTeamLog::class.java) }
        verify { mockQuery.equalTo("type", "teamVisit") }
        verify { mockQuery.equalTo("user", "testUser") }
        verify { mockQuery.equalTo("teamId", "testTeamId") }
        verify { mockQuery.max("time") }
    }

    @Test
    fun testSerializeTeamActivities() {
        val log = RealmTeamLog().apply {
            user = "testUser"
            type = "testType"
            createdOn = "testDate"
            parentCode = "testParent"
            teamType = "testTeamType"
            time = 12345L
            teamId = "testTeamId"
            _rev = "testRev"
            _id = "testId"
        }
        val mockContext = mockk<Context>()

        every { NetworkUtils.getUniqueIdentifier() } returns "testUniqueId"
        every { NetworkUtils.getDeviceName() } returns "testDevice"
        every { NetworkUtils.getCustomDeviceName(mockContext) } returns "testCustomDevice"
        every { TextUtils.isEmpty("testRev") } returns false

        val result = RealmTeamLog.serializeTeamActivities(log, mockContext)

        assertEquals("testUser", result.get("user").asString)
        assertEquals("testType", result.get("type").asString)
        assertEquals("testDate", result.get("createdOn").asString)
        assertEquals("testParent", result.get("parentCode").asString)
        assertEquals("testTeamType", result.get("teamType").asString)
        assertEquals(12345L, result.get("time").asLong)
        assertEquals("testTeamId", result.get("teamId").asString)
        assertEquals("testUniqueId", result.get("androidId").asString)
        assertEquals("testDevice", result.get("deviceName").asString)
        assertEquals("testCustomDevice", result.get("customDeviceName").asString)
        assertEquals("testRev", result.get("_rev").asString)
        assertEquals("testId", result.get("_id").asString)
    }

    @Test
    fun testSerializeTeamActivities_emptyRev() {
        val log = RealmTeamLog().apply {
            user = "testUser"
            type = "testType"
            createdOn = "testDate"
            parentCode = "testParent"
            teamType = "testTeamType"
            time = 12345L
            teamId = "testTeamId"
            _rev = ""
            _id = "testId"
        }
        val mockContext = mockk<Context>()

        every { NetworkUtils.getUniqueIdentifier() } returns "testUniqueId"
        every { NetworkUtils.getDeviceName() } returns "testDevice"
        every { NetworkUtils.getCustomDeviceName(mockContext) } returns "testCustomDevice"
        every { TextUtils.isEmpty("") } returns true

        val result = RealmTeamLog.serializeTeamActivities(log, mockContext)

        assertEquals(false, result.has("_rev"))
        assertEquals(false, result.has("_id"))
    }

    @Test
    fun testInsert_newObject() {
        val mockRealm = mockk<Realm>()
        val mockQuery = mockk<RealmQuery<RealmTeamLog>>()
        val act = JsonObject().apply {
            addProperty("_id", "newId")
            addProperty("_rev", "newRev")
            addProperty("type", "newType")
            addProperty("user", "newUser")
            addProperty("createdOn", "newDate")
            addProperty("parentCode", "newParent")
            addProperty("time", 98765L)
            addProperty("teamId", "newTeamId")
            addProperty("teamType", "newTeamType")
        }
        val newLog = mockk<RealmTeamLog>(relaxed = true)

        every { JsonUtils.getString("_id", act) } returns "newId"
        every { JsonUtils.getString("_rev", act) } returns "newRev"
        every { JsonUtils.getString("type", act) } returns "newType"
        every { JsonUtils.getString("user", act) } returns "newUser"
        every { JsonUtils.getString("createdOn", act) } returns "newDate"
        every { JsonUtils.getString("parentCode", act) } returns "newParent"
        every { JsonUtils.getString("teamId", act) } returns "newTeamId"
        every { JsonUtils.getString("teamType", act) } returns "newTeamType"
        every { JsonUtils.getLong("time", act) } returns 98765L

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "newId") } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmTeamLog::class.java, "newId") } returns newLog

        RealmTeamLog.insert(mockRealm, act)

        verify { newLog._rev = "newRev" }
        verify { newLog._id = "newId" }
        verify { newLog.type = "newType" }
        verify { newLog.user = "newUser" }
        verify { newLog.createdOn = "newDate" }
        verify { newLog.parentCode = "newParent" }
        verify { newLog.time = 98765L }
        verify { newLog.teamId = "newTeamId" }
        verify { newLog.teamType = "newTeamType" }
    }

    @Test
    fun testInsert_existingObject() {
        val mockRealm = mockk<Realm>()
        val mockQuery = mockk<RealmQuery<RealmTeamLog>>()
        val act = JsonObject().apply {
            addProperty("_id", "existingId")
            addProperty("_rev", "updatedRev")
            addProperty("type", "updatedType")
            addProperty("user", "updatedUser")
            addProperty("createdOn", "updatedDate")
            addProperty("parentCode", "updatedParent")
            addProperty("time", 11111L)
            addProperty("teamId", "updatedTeamId")
            addProperty("teamType", "updatedTeamType")
        }
        val existingLog = mockk<RealmTeamLog>(relaxed = true)

        every { JsonUtils.getString("_id", act) } returns "existingId"
        every { JsonUtils.getString("_rev", act) } returns "updatedRev"
        every { JsonUtils.getString("type", act) } returns "updatedType"
        every { JsonUtils.getString("user", act) } returns "updatedUser"
        every { JsonUtils.getString("createdOn", act) } returns "updatedDate"
        every { JsonUtils.getString("parentCode", act) } returns "updatedParent"
        every { JsonUtils.getString("teamId", act) } returns "updatedTeamId"
        every { JsonUtils.getString("teamType", act) } returns "updatedTeamType"
        every { JsonUtils.getLong("time", act) } returns 11111L

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "existingId") } returns mockQuery
        every { mockQuery.findFirst() } returns existingLog

        RealmTeamLog.insert(mockRealm, act)

        verify(exactly = 0) { mockRealm.createObject(RealmTeamLog::class.java, any<String>()) }
        verify { existingLog._rev = "updatedRev" }
        verify { existingLog._id = "existingId" }
        verify { existingLog.type = "updatedType" }
        verify { existingLog.user = "updatedUser" }
        verify { existingLog.createdOn = "updatedDate" }
        verify { existingLog.parentCode = "updatedParent" }
        verify { existingLog.time = 11111L }
        verify { existingLog.teamId = "updatedTeamId" }
        verify { existingLog.teamType = "updatedTeamType" }
    }
}
