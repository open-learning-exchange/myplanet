package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils

class DictionaryMapperTest {

    @Test
    fun `mapJsonArrayToEntities maps valid json fields correctly including advance_code and antonoym`() {
        val jsonString = """
            [
                {
                    "code": "en_1",
                    "language": "en",
                    "advance_code": "adv_100",
                    "word": "abundant",
                    "meaning": "existing or available in large quantities",
                    "definition": "Plentiful",
                    "synonym": "plentiful",
                    "antonoym": "scarce"
                }
            ]
        """.trimIndent()

        val jsonArray = JsonUtils.gson.fromJson(jsonString, JsonArray::class.java)
        val entities = DictionaryMapper.mapJsonArrayToEntities(jsonArray)

        assertEquals(1, entities.size)
        val entity = entities[0]
        assertTrue(entity.id.isNotEmpty())
        assertEquals("en_1", entity.code)
        assertEquals("en", entity.language)
        assertEquals("adv_100", entity.advanceCode)
        assertEquals("abundant", entity.word)
        assertEquals("existing or available in large quantities", entity.meaning)
        assertEquals("Plentiful", entity.definition)
        assertEquals("plentiful", entity.synonym)
        assertEquals("scarce", entity.antonym)
    }

    @Test
    fun `mapJsonArrayToEntities assigns unique IDs to each entity`() {
        val jsonString = """
            [
                {"word": "a"},
                {"word": "b"}
            ]
        """.trimIndent()

        val jsonArray = JsonUtils.gson.fromJson(jsonString, JsonArray::class.java)
        val entities = DictionaryMapper.mapJsonArrayToEntities(jsonArray)

        assertEquals(2, entities.size)
        assertNotEquals(entities[0].id, entities[1].id)
    }

    @Test
    fun `mapJsonArrayToEntities handles empty json array`() {
        val jsonArray = JsonArray()
        val entities = DictionaryMapper.mapJsonArrayToEntities(jsonArray)

        assertTrue(entities.isEmpty())
    }

    @Test
    fun `mapJsonArrayToEntities handles missing fields with default empty strings`() {
        val jsonString = """[{}]"""
        val jsonArray = JsonUtils.gson.fromJson(jsonString, JsonArray::class.java)
        val entities = DictionaryMapper.mapJsonArrayToEntities(jsonArray)

        assertEquals(1, entities.size)
        val entity = entities[0]
        assertEquals("", entity.code)
        assertEquals("", entity.language)
        assertEquals("", entity.advanceCode)
        assertEquals("", entity.word)
        assertEquals("", entity.meaning)
        assertEquals("", entity.definition)
        assertEquals("", entity.synonym)
        assertEquals("", entity.antonym)
    }
}
