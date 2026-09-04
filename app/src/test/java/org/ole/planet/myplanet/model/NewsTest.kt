package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsTest {

    @Test
    fun testUpdateMessage() {
        val news = News()
        news.message = "Original message"
        val before = System.currentTimeMillis()

        news.updateMessage("Updated message")

        val after = System.currentTimeMillis()

        assertEquals("Updated message", news.message)
        assertTrue(news.isEdited)
        assertTrue("editedTime $before <= ${news.editedTime} <= $after", news.editedTime in before..after)
    }

    @Test
    fun testCreateNewsWithoutNewsKey() {
        val map = HashMap<String?, String>()
        map["message"] = "Hello world"
        map["messagePlanetCode"] = "code1"
        map["messageType"] = "type1"
        map["updatedDate"] = "100"

        val user = UserEntity().apply {
            id = "user_id_1"
            name = "John Doe"
            planetCode = "planet_code_1"
            parentCode = "parent_code_1"
        }

        val before = System.currentTimeMillis()
        val news = News.createNews(map, user, listOf("image1.png", "image2.png"))
        val after = System.currentTimeMillis()

        assertNotNull(news.id)
        assertEquals("Hello world", news.message)
        assertTrue("time $before <= ${news.time} <= $after", news.time in before..after)
        assertEquals("planet_code_1", news.createdOn)
        assertEquals("John Doe", news.userName)
        assertEquals("parent_code_1", news.parentCode)
        assertEquals("code1", news.messagePlanetCode)
        assertEquals("type1", news.messageType)
        assertEquals(100L, news.updatedDate)
        assertEquals("user_id_1", news.userId)
        assertEquals(listOf("image1.png", "image2.png"), news.imageUrls)
        assertNull(news.newsId)
        assertNull(news.newsRev)
        assertNull(news.newsUser)
        assertNull(news.aiProvider)
        assertNull(news.newsTitle)
    }

    @Test
    fun testCreateNewsWithNewsKey() {
        val map = HashMap<String?, String>()
        map["message"] = "News message"
        map["news"] = "{'_id':'news_123','_rev':'rev_123','user':'news_user','aiProvider':'openai','title':'News Title','createdDate':'1000','updatedDate':'2000'}"

        val news = News.createNews(map, null, null)

        assertEquals("news_123", news.newsId)
        assertEquals("rev_123", news.newsRev)
        assertEquals("news_user", news.newsUser)
        assertEquals("openai", news.aiProvider)
        assertEquals("News Title", news.newsTitle)
        assertEquals(1000L, news.newsCreatedDate)
        assertEquals(2000L, news.newsUpdatedDate)
    }

    @Test
    fun testCreateNewsWithNewsKeyAndConversations() {
        val map = HashMap<String?, String>()
        map["message"] = "News message"
        val conversationsJson = "[{'_id':'1','query':'Q1','response':'R1'}]"
        map["news"] = "{'_id':'news_123','conversations':'[{\"query\":\"Q1\",\"response\":\"R1\"}]'}"

        val news = News.createNews(map, null, null)

        assertEquals("news_123", news.newsId)
        assertNotNull(news.conversations)
        assertTrue(news.conversations!!.contains("Q1"))
        assertTrue(news.conversations!!.contains("R1"))
    }

    @Test
    fun testCreateNewsWithNullNewsKey() {
        val map = HashMap<String?, String>()
        map["message"] = "News message"
        map["news"] = null.toString() // Or map directly with key having null value if using map that allows null values

        val mapWithNull = HashMap<String?, String?>()
        mapWithNull["message"] = "Test"
        @Suppress("UNCHECKED_CAST")
        val news = News.createNews(mapWithNull as HashMap<String?, String>, null, null)

        assertNull(news.newsId)
        assertNull(news.newsRev)
    }
}
