package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

class AchievementTest {

    /** A Gson-built document handed over the way the Gson callers do: bridged with toKotlinx(). */
    private fun gsonDoc(block: JsonObject.() -> Unit) = JsonObject().apply(block).toKotlinx().jsonObject

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

        val achievement = Achievement.fromJson(act.toKotlinx().jsonObject)

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

        assertEquals(2, achievement.achievementsArray.toGson().size())
        assertEquals("completed-course-a", achievement.achievementsArray.toGson()[0].asString)
        assertEquals("completed-course-b", achievement.achievementsArray.toGson()[1].asString)

        assertEquals(1, achievement.getReferencesArray().toGson().size())
        val refObj = achievement.getReferencesArray().toGson()[0].asJsonObject
        assertEquals("Jane Doe", refObj.get("name").asString)
        assertEquals("mentor", refObj.get("relationship").asString)

        assertEquals(1, achievement.linksArray.toGson().size())
        assertEquals("https://example.com/resource-1", achievement.linksArray.toGson()[0].asString)

        assertEquals(1, achievement.otherInfoArray.toGson().size())
        assertEquals("extra-info", achievement.otherInfoArray.toGson()[0].asString)
    }

    @Test
    fun parseStringListToJsonArray_neverHandsOutTheCachedInstance() {
        val achievement = Achievement.fromJson(gsonDoc {
            addProperty("_id", "ach_cache")
            add("achievements", JsonArray().apply {
                add(JsonObject().apply { addProperty("title", "cached-value") })
            })
        })

        val firstRead = achievement.achievementsArray.toGson()
        val secondRead = achievement.achievementsArray.toGson()

        // Each read bridged to Gson (as the callers do) is its own mutable copy, so mutating one cannot reach the cache
        assertTrue(firstRead[0] !== secondRead[0])
        firstRead[0].asJsonObject.addProperty("title", "mutated")

        assertEquals("cached-value", secondRead[0].asJsonObject.get("title").asString)
        assertEquals("cached-value", achievement.achievementsArray.toGson()[0].asJsonObject.get("title").asString)
    }

    @Test
    fun parseStringListToJsonArray_isolatesRecordsWithIdenticalContent() {
        val entry = JsonObject().apply {
            addProperty("title", "shared-value")
        }
        val ach1 = Achievement.fromJson(gsonDoc {
            addProperty("_id", "ach_cache_1")
            add("achievements", JsonArray().apply { add(entry) })
        })
        val ach2 = Achievement.fromJson(gsonDoc {
            addProperty("_id", "ach_cache_2")
            add("achievements", JsonArray().apply { add(entry) })
        })

        // Two records whose field serializes to the same string share the cache entry but not
        // the instances, so one record cannot corrupt the other
        ach1.achievementsArray.toGson()[0].asJsonObject.addProperty("title", "mutated")

        assertEquals("shared-value", ach1.achievementsArray.toGson()[0].asJsonObject.get("title").asString)
        assertEquals("shared-value", ach2.achievementsArray.toGson()[0].asJsonObject.get("title").asString)
    }

    @Test
    fun parseStringListToJsonArray_copyIsFreeForImmutablePrimitives() {
        val achievement = Achievement.fromJson(gsonDoc {
            addProperty("_id", "ach_primitives")
            add("links", JsonArray().apply { add("https://example.com/resource-1") })
        })

        // kotlinx elements are immutable, so the cached element itself is handed out
        assertTrue(achievement.linksArray[0] === achievement.linksArray[0])
    }

    @Test
    fun parseStringListToJsonArray_handlesNullList() {
        val achievement = Achievement().apply { _id = "empty_ach" }
        assertEquals(0, achievement.achievementsArray.toGson().size())
        assertEquals(0, achievement.getReferencesArray().toGson().size())
        assertEquals(0, achievement.linksArray.toGson().size())
        assertEquals(0, achievement.otherInfoArray.toGson().size())
    }

    @Test
    fun serialize_roundTripsFieldsAndArrays() {
        val original = Achievement.fromJson(gsonDoc {
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
                add(Achievement.createReference("Bob", "colleague", "555-2000", "bob@example.com").toGson())
            })
            add("achievements", JsonArray().apply { add("course-x") })
            add("links", JsonArray().apply { add("https://example.com/x") })
            add("otherInfo", JsonArray().apply { add("info") })
        })

        val serialized = Achievement.serialize(original).toGson()

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
        val ref = Achievement.createReference("Alice", "advisor", "555-3000", "alice@example.com").toGson()
        assertEquals("Alice", ref.get("name").asString)
        assertEquals("advisor", ref.get("relationship").asString)
        assertEquals("555-3000", ref.get("phone").asString)
        assertEquals("alice@example.com", ref.get("email").asString)
    }

    @Test
    fun parsedJsonCache_isBoundedToCapacity() {
        // Cached and freshly parsed elements are indistinguishable through the public API. The bound is therefore verified by
        // observing the shared process-wide cache directly. The cache is populated by
        // parseStringListToJsonArray (reached via the achievementsArray getter, not the
        // setter), so each distinct entry is read back to fill the cache. Pushing more than
        // CACHE_CAPACITY distinct entries must evict the eldest and cap the size instead of
        // growing unbounded.
        Achievement.parsedJsonCache.clear()
        val achievement = Achievement().apply { _id = "bound_ach" }
        val overCapacity = Achievement.CACHE_CAPACITY + 500
        for (i in 1..overCapacity) {
            achievement.setAchievements(JsonArray().apply { add("bound-$i") }.toKotlinx().jsonArray)
            achievement.achievementsArray.toGson().size()
        }
        assertEquals(Achievement.CACHE_CAPACITY, Achievement.parsedJsonCache.size)
    }
}
