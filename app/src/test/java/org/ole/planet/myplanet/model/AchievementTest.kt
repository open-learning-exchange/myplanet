package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTest {

    @Test
    fun fromJson_setsFieldsAndParsesStringListArrays() {
        val references = JsonObject().apply {
            addProperty("name", "Jane Doe")
            addProperty("relationship", "mentor")
            addProperty("phone", "555-0100")
            addProperty("email", "jane@example.com")
        }
        val referencesArray = JsonArray().apply { add(references) }

        val achievementsArray = JsonArray().apply {
            add("completed-course-a")
            add("completed-course-b")
        }
        val linksArray = JsonArray().apply {
            add("https://example.com/resource-1")
        }
        val otherInfoArray = JsonArray().apply {
            add("extra-info")
        }

        val act = JsonObject().apply {
            addProperty("_id", "ach_1")
            addProperty("_rev", "1-abc")
            addProperty("purpose", "learning")
            addProperty("goals", "learn kotlin")
            addProperty("achievementsHeader", "My Achievements")
            addProperty("sendToNation", "true")
            addProperty("dateSortOrder", "desc")
            addProperty("createdOn", "2026-01-01")
            addProperty("username", "learner")
            addProperty("parentCode", "US")
            addProperty("resumeFileName", "resume.pdf")
            add("references", referencesArray)
            add("achievements", achievementsArray)
            add("links", linksArray)
            add("otherInfo", otherInfoArray)
        }

        val achievement = Achievement.fromJson(act)

        assertEquals("ach_1", achievement._id)
        assertEquals("1-abc", achievement._rev)
        assertEquals("learning", achievement.purpose)
        assertEquals("learn kotlin", achievement.goals)
        assertEquals("My Achievements", achievement.achievementsHeader)
        assertEquals("true", achievement.sendToNation)
        assertEquals("desc", achievement.dateSortOrder)
        assertEquals("2026-01-01", achievement.createdOn)
        assertEquals("learner", achievement.username)
        assertEquals("US", achievement.parentCode)
        assertEquals("resume.pdf", achievement.resumeFileName)
        assertFalse(achievement.isUpdated)

        assertEquals(2, achievement.achievementsArray.size())
        assertEquals("completed-course-a", achievement.achievementsArray[0].asString)
        assertEquals("completed-course-b", achievement.achievementsArray[1].asString)

        assertEquals(1, achievement.getReferencesArray().size())
        val refObj = achievement.getReferencesArray()[0].asJsonObject
        assertEquals("Jane Doe", refObj.get("name").asString)
        assertEquals("mentor", refObj.get("relationship").asString)

        assertEquals(1, achievement.linksArray.size())
        assertEquals("https://example.com/resource-1", achievement.linksArray[0].asString)

        assertEquals(1, achievement.otherInfoArray.size())
        assertEquals("extra-info", achievement.otherInfoArray[0].asString)
    }

    @Test
    fun parseStringListToJsonArray_returnsCachedElementViaDeepCopy() {
        // A JsonObject entry is serialized to a string, then parsed back and cached.
        // deepCopy() produces a fresh JsonObject for each read, so repeated reads of the
        // same cached entry are not the same instance (mutations of one won't leak to the other).
        val entry = JsonObject().apply {
            addProperty("title", "cached-value")
        }
        val first = Achievement.fromJson(JsonObject().apply {
            addProperty("_id", "ach_cache")
            add("achievements", JsonArray().apply { add(entry) })
        }).achievementsArray

        val second = Achievement.fromJson(JsonObject().apply {
            addProperty("_id", "ach_cache_2")
            add("achievements", JsonArray().apply { add(entry) })
        }).achievementsArray

        assertEquals("cached-value", first[0].asJsonObject.get("title").asString)
        assertEquals("cached-value", second[0].asJsonObject.get("title").asString)
        // deepCopy ensures cached entries aren't shared by identity
        assertTrue(first[0] !== second[0])
    }

    @Test
    fun parseStringListToJsonArray_handlesNullList() {
        val achievement = Achievement().apply { _id = "empty_ach" }
        assertEquals(0, achievement.achievementsArray.size())
        assertEquals(0, achievement.getReferencesArray().size())
        assertEquals(0, achievement.linksArray.size())
        assertEquals(0, achievement.otherInfoArray.size())
    }

    @Test
    fun serialize_roundTripsFieldsAndArrays() {
        val original = Achievement.fromJson(JsonObject().apply {
            addProperty("_id", "ach_serialize")
            addProperty("_rev", "2-rev")
            addProperty("purpose", "growth")
            addProperty("goals", "grow")
            addProperty("achievementsHeader", "Header")
            addProperty("sendToNation", "false")
            addProperty("dateSortOrder", "none")
            addProperty("createdOn", "2026-02-02")
            addProperty("username", "user")
            addProperty("parentCode", "DE")
            addProperty("resumeFileName", "cv.pdf")
            add("references", JsonArray().apply {
                add(Achievement.createReference("Bob", "colleague", "555-2000", "bob@example.com"))
            })
            add("achievements", JsonArray().apply { add("course-x") })
            add("links", JsonArray().apply { add("https://example.com/x") })
            add("otherInfo", JsonArray().apply { add("info") })
        })

        val serialized = Achievement.serialize(original)

        assertEquals("ach_serialize", serialized.get("_id").asString)
        assertEquals("2-rev", serialized.get("_rev").asString)
        assertEquals("growth", serialized.get("purpose").asString)
        assertEquals("grow", serialized.get("goals").asString)
        assertEquals("Header", serialized.get("achievementsHeader").asString)
        assertEquals(false, serialized.get("sendToNation").asBoolean)
        assertEquals("none", serialized.get("dateSortOrder").asString)
        assertEquals("2026-02-02", serialized.get("createdOn").asString)
        assertEquals("user", serialized.get("username").asString)
        assertEquals("DE", serialized.get("parentCode").asString)
        assertEquals("cv.pdf", serialized.get("resumeFileName").asString)
        assertEquals(1, serialized.getAsJsonArray("achievements").size())
        assertEquals(1, serialized.getAsJsonArray("links").size())
        assertEquals(1, serialized.getAsJsonArray("otherInfo").size())
        assertEquals("Bob", serialized.getAsJsonArray("references")[0].asJsonObject.get("name").asString)
    }

    @Test
    fun createReference_buildsExpectedJsonObject() {
        val ref = Achievement.createReference("Alice", "advisor", "555-3000", "alice@example.com")
        assertEquals("Alice", ref.get("name").asString)
        assertEquals("advisor", ref.get("relationship").asString)
        assertEquals("555-3000", ref.get("phone").asString)
        assertEquals("alice@example.com", ref.get("email").asString)
    }

    @Test
    fun parsedJsonCache_isBoundedToCapacity() {
        // deepCopy() is applied on every read, so cached and freshly parsed elements are
        // indistinguishable through the public API. The bound is therefore verified by
        // observing the shared process-wide cache directly. The cache is populated by
        // parseStringListToJsonArray (reached via the achievementsArray getter, not the
        // setter), so each distinct entry is read back to fill the cache. Pushing more than
        // CACHE_CAPACITY distinct entries must evict the eldest and cap the size instead of
        // growing unbounded.
        Achievement.parsedJsonCache.clear()
        val achievement = Achievement().apply { _id = "bound_ach" }
        val overCapacity = Achievement.CACHE_CAPACITY + 500
        for (i in 1..overCapacity) {
            achievement.setAchievements(JsonArray().apply { add("bound-$i") })
            achievement.achievementsArray.size()
        }
        assertEquals(Achievement.CACHE_CAPACITY, Achievement.parsedJsonCache.size)
    }
}
