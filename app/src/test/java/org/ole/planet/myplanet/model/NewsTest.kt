package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        map["news"] = "{'_id':'news_123','conversations':'[{\"query\":\"Q1\",\"response\":\"R1\"}]'}"

        val news = News.createNews(map, null, null)

        assertEquals("news_123", news.newsId)
        assertNotNull(news.conversations)
        assertTrue(news.conversations!!.contains("Q1"))
        assertTrue(news.conversations!!.contains("R1"))
    }

    @Test
    fun testCreateNewsWithNullNewsKey() {
        val mapWithNull = HashMap<String?, String?>()
        mapWithNull["message"] = "Test"
        @Suppress("UNCHECKED_CAST")
        val news = News.createNews(mapWithNull as HashMap<String?, String>, null, null)

        assertNull(news.newsId)
        assertNull(news.newsRev)
    }

    @Test
    fun `imagesArray memoizes parsed JsonArray when called multiple times`() {
        val news = News()
        val jsonString = """[{"resourceId":"res123"}]"""
        news.images = jsonString

        val firstCall = news.imagesArray
        assertNotNull(firstCall)
        assertEquals(1, firstCall.size())
        assertEquals("res123", firstCall[0].asJsonObject.get("resourceId").asString)

        val secondCall = news.imagesArray
        assertEquals(firstCall, secondCall)
    }

    @Test
    fun `imagesArray defensive copy prevents mutating cached instance`() {
        val news = News()
        news.images = """[{"resourceId":"res123"}]"""

        val firstCall = news.imagesArray
        firstCall.add(JsonObject().apply { addProperty("resourceId", "mutated") })
        assertEquals(2, firstCall.size())

        val secondCall = news.imagesArray
        assertEquals(1, secondCall.size())
        assertEquals("res123", secondCall[0].asJsonObject.get("resourceId").asString)
    }

    @Test
    fun `imagesArray invalidates cache and re-parses when images is reassigned`() {
        val news = News()
        news.images = """[{"resourceId":"res123"}]"""

        val firstCall = news.imagesArray
        assertEquals("res123", firstCall[0].asJsonObject.get("resourceId").asString)

        news.images = """[{"resourceId":"res456"}]"""

        val secondCall = news.imagesArray
        assertEquals(1, secondCall.size())
        assertEquals("res456", secondCall[0].asJsonObject.get("resourceId").asString)

        news.images = null
        val thirdCall = news.imagesArray
        assertTrue(thirdCall.isEmpty)
    }

    @Test
    fun `imagesArray returns empty JsonArray for null images string`() {
        val news = News()
        news.images = null

        val result = news.imagesArray
        assertNotNull(result)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `isCommunityNews uses parsedViewIn if present even when viewIn is null or empty`() {
        val news = News()
        val communityArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("section", "community")
            })
        }
        news.parsedViewIn = communityArray
        news.viewIn = null

        assertTrue(news.isCommunityNews)

        news.viewIn = """[{"section":"other"}]"""
        assertTrue(news.isCommunityNews)
    }

    @Test
    fun `isCommunityNews falls back to viewIn parsing when parsedViewIn is null`() {
        val news = News()
        news.parsedViewIn = null
        news.viewIn = """[{"section":"community"}]"""

        assertTrue(news.isCommunityNews)

        news.viewIn = """[{"section":"my_courses"}]"""
        assertFalse(news.isCommunityNews)
    }

    @Test
    fun `isCommunityNews handles null or invalid viewIn gracefully`() {
        val news = News()
        news.viewIn = null
        news.parsedViewIn = null
        assertFalse(news.isCommunityNews)

        news.viewIn = "not a valid json"
        assertFalse(news.isCommunityNews)
    }
}
