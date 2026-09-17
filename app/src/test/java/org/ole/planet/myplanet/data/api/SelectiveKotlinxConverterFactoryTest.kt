package org.ole.planet.myplanet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.MyPlanet
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SelectiveKotlinxConverterFactoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val handledTypes = setOf<java.lang.reflect.Type>(
        MyPlanet::class.java,
        ChatResponse::class.java,
        DocumentResponse::class.java
    )
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .addConverterFactory(SelectiveKotlinxConverterFactory(json, handledTypes))
        .addConverterFactory(GsonConverterFactory.create(Gson()))
        .build()

    @Test
    fun `deserializes a handled type through kotlinx serialization`() {
        val body = """{"appname":"myPlanet","planetVersion":"1.2.3","minapkcode":1,"latestapkcode":2}"""
            .toResponseBody(null)

        val converter = retrofit.responseBodyConverter<MyPlanet>(MyPlanet::class.java, emptyArray())
        val result = converter.convert(body)

        assertEquals("myPlanet", result?.appname)
        assertEquals("1.2.3", result?.planetVersion)
        assertEquals(1, result?.minapkcode)
        assertEquals(2, result?.latestapkcode)
    }

    @Test
    fun `deserializes nested handled type through kotlinx serialization`() {
        val body = """{"message":"hi","couchDBResponse":{"ok":true,"id":"abc","rev":"1-x"}}"""
            .toResponseBody(null)

        val converter = retrofit.responseBodyConverter<ChatResponse>(ChatResponse::class.java, emptyArray())
        val result = converter.convert(body)

        assertEquals("hi", result?.message)
        assertEquals(true, result?.couchDBResponse?.ok)
        assertEquals("abc", result?.couchDBResponse?.id)
    }

    @Test
    fun `falls through to gson for unhandled raw JsonObject responses`() {
        val body = """{"_id":"doc1","field":"value"}""".toResponseBody(null)

        val converter = retrofit.responseBodyConverter<JsonObject>(JsonObject::class.java, emptyArray())
        val result = converter.convert(body)

        assertTrue(result is JsonObject)
        assertEquals("doc1", (result as JsonObject).get("_id").asString)
    }

    @Test
    fun `factory itself returns null for unhandled types`() {
        val factory = SelectiveKotlinxConverterFactory(json, handledTypes)
        val converter = factory.responseBodyConverter(JsonObject::class.java, emptyArray(), retrofit)

        assertNull(converter)
    }
}
