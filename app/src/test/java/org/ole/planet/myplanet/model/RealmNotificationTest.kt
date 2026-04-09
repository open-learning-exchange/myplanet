package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealmNotificationTest {

    @Test
    fun `test default property values`() {
        val notification = RealmNotification()
        assertNotNull(notification.id)
        assertEquals("", notification.userId)
        assertEquals("", notification.message)
        assertFalse(notification.isRead)
        assertNotNull(notification.createdAt)
        assertEquals("", notification.type)
        assertEquals(null, notification.relatedId)
        assertEquals(null, notification.title)
        assertEquals(null, notification.link)
        assertEquals(0, notification.priority)
        assertFalse(notification.isFromServer)
        assertEquals(null, notification.rev)
        assertFalse(notification.needsSync)
    }

    @Test
    fun `insert with missing id does nothing`() {
        val realm = mockk<Realm>(relaxed = true)
        val jsonObject = JsonObject() // Missing _id

        RealmNotification.insert(realm, jsonObject)

        verify(exactly = 0) { realm.where(RealmNotification::class.java) }
        verify(exactly = 0) { realm.createObject(RealmNotification::class.java, any<String>()) }
    }

    @Test
    fun `insert creates new notification when not found`() {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val notification = RealmNotification()
        val jsonObject = JsonObject().apply {
            addProperty("_id", "testId")
            addProperty("user", "testUser")
            addProperty("message", "testMessage")
            addProperty("type", "testType")
            addProperty("link", "testLink")
            addProperty("priority", 1)
            addProperty("_rev", "testRev")
            addProperty("status", "read")
            addProperty("time", 123456789L)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.equalTo("id", "testId") } returns query
        every { query.findFirst() } returns null
        every { realm.createObject(RealmNotification::class.java, "testId") } returns notification

        RealmNotification.insert(realm, jsonObject)

        assertEquals("testUser", notification.userId)
        assertEquals("testMessage", notification.message)
        assertEquals("testType", notification.type)
        assertEquals("testLink", notification.link)
        assertEquals(1, notification.priority)
        assertEquals("testRev", notification.rev)
        assertTrue(notification.isRead) // "read" != "unread"
        assertEquals(123456789L, notification.createdAt.time)
        assertTrue(notification.isFromServer)
    }

    @Test
    fun `insert updates existing notification`() {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val notification = RealmNotification()
        val jsonObject = JsonObject().apply {
            addProperty("_id", "testId")
            addProperty("user", "updatedUser")
            addProperty("message", "updatedMessage")
            addProperty("type", "updatedType")
            addProperty("status", "unread")
            addProperty("time", 987654321L)
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.equalTo("id", "testId") } returns query
        every { query.findFirst() } returns notification

        RealmNotification.insert(realm, jsonObject)

        assertEquals("updatedUser", notification.userId)
        assertEquals("updatedMessage", notification.message)
        assertEquals("updatedType", notification.type)
        assertFalse(notification.isRead) // "unread" == "unread" -> isRead = false
        assertEquals(987654321L, notification.createdAt.time)
        assertTrue(notification.isFromServer)

        verify(exactly = 0) { realm.createObject(any<Class<RealmNotification>>(), any<String>()) }
    }

    @Test
    fun `insert preserves read status if needsSync is true`() {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmNotification>>()
        val notification = RealmNotification().apply {
            isRead = true
            needsSync = true
        }
        val jsonObject = JsonObject().apply {
            addProperty("_id", "testId")
            addProperty("status", "unread")
        }

        every { realm.where(RealmNotification::class.java) } returns query
        every { query.equalTo("id", "testId") } returns query
        every { query.findFirst() } returns notification

        RealmNotification.insert(realm, jsonObject)

        // isRead should be preserved (true) even though status is "unread", because needsSync is true
        assertTrue(notification.isRead)
    }
}
