package org.ole.planet.myplanet.model

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.realm.Realm
import io.realm.RealmQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.NetworkUtils

class RealmTeamLogTest {

    @Before
    fun setup() {
        mockkObject(NetworkUtils)
        mockkStatic(TextUtils::class)
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
        val existingLog = RealmTeamLog()

        val jsonObject = JsonObject().apply {
            addProperty("_id", "id123")
            addProperty("_rev", "rev123")
            addProperty("type", "teamVisit")
            addProperty("user", "testUser")
            addProperty("createdOn", "2023-01-01")
            addProperty("parentCode", "parent123")
            addProperty("time", 123456789L)
            addProperty("teamId", "testTeam")
            addProperty("teamType", "testType")
        }

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "id123") } returns mockQuery
        every { mockQuery.findFirst() } returns existingLog

        RealmTeamLog.insert(mockRealm, jsonObject)

        assertEquals("rev123", existingLog._rev)
        assertEquals("id123", existingLog._id)
        assertEquals("teamVisit", existingLog.type)
        assertEquals("testUser", existingLog.user)
        assertEquals("2023-01-01", existingLog.createdOn)
        assertEquals("parent123", existingLog.parentCode)
        assertEquals(123456789L, existingLog.time)
        assertEquals("testTeam", existingLog.teamId)
        assertEquals("testType", existingLog.teamType)
    }

    @Test
    fun testInsert_newLog() {
        val mockRealm = mockk<Realm>()
        val mockQuery = mockk<RealmQuery<RealmTeamLog>>()
        val newLog = RealmTeamLog()

        val jsonObject = JsonObject().apply {
            addProperty("_id", "id123")
            addProperty("_rev", "rev123")
            addProperty("type", "teamVisit")
            addProperty("user", "testUser")
            addProperty("createdOn", "2023-01-01")
            addProperty("parentCode", "parent123")
            addProperty("time", 123456789L)
            addProperty("teamId", "testTeam")
            addProperty("teamType", "testType")
        }

        every { mockRealm.where(RealmTeamLog::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", "id123") } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmTeamLog::class.java, "id123") } returns newLog

        RealmTeamLog.insert(mockRealm, jsonObject)

        assertEquals("rev123", newLog._rev)
        assertEquals("id123", newLog._id)
        assertEquals("teamVisit", newLog.type)
        assertEquals("testUser", newLog.user)
        assertEquals("2023-01-01", newLog.createdOn)
        assertEquals("parent123", newLog.parentCode)
        assertEquals(123456789L, newLog.time)
        assertEquals("testTeam", newLog.teamId)
        assertEquals("testType", newLog.teamType)
    }
}
