package org.ole.planet.myplanet.utils

import com.google.gson.Gson
import com.google.gson.JsonObject as GsonJsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.di.NetworkModule
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden

/**
 * Proves kotlinx can read every JSON format Gson has already written to devices (Room columns,
 * preferences), so a later switch of the reader keeps existing data readable. The inputs are the
 * checked-in raw goldens, i.e. the exact bytes the current Gson code persists, including its
 * `\u003c`-style HTML escapes.
 *
 * It also pins how Gson's reflective `fromJson` and the app's kotlinx `Json` disagree on
 * malformed or mistyped input, for the typed formats the app persists.
 */
class PersistedFormatReadabilityTest {

    private val goldenRoot = File("src/test/resources/json-golden")
    private val appJson: Json = NetworkModule.provideJson()

    private fun golden(path: String) = File(goldenRoot, path).readText()

    private fun column(path: String, key: String, terminator: String? = null): List<String> =
        golden(path).lines().filter { it.startsWith("$key=") }.map {
            val value = it.removePrefix("$key=")
            if (terminator != null) value.substringBefore(terminator) else value
        }

    /** Every Gson-written persisted string, by where it came from. */
    private fun persistedSamples(): Map<String, String> = buildMap {
        put("savedUsers", golden("services/savedUsers.txt"))
        put("feedback.messages", golden("model/persisted/Feedback-messages.txt"))
        put("certification.courseIds", golden("model/persisted/Certification-courseIds.txt"))
        put("news.user", golden("model/persisted/News-createNews-user.txt"))
        put("news.conversations", golden("model/persisted/News-createNews-conversations.txt"))
        put("news.viewIn", golden("model/persisted/News-createNews-viewIn.txt"))
        put("chatShare.news", golden("model/persisted/ChatSharePayload-news.txt"))
        golden("model/persisted/Achievement-achievements.txt").lines().forEachIndexed { i, line -> put("achievement.$i", line) }
        put("feedback.addReply", golden("repository/feedback/addReply-messages.txt"))
        put("feedback.created", golden("repository/feedback/createFeedback-messages.txt"))
        column("repository/feedback/synced-messages.txt", "messages", " openTime=").forEach { put("feedback.synced", it) }
        column("repository/events/createMeetup-columns.txt", "sync").forEach { put("meetup.sync", it) }
        column("repository/events/createMeetup-columns.txt", "link").forEach { put("meetup.link", it) }
        column("repository/teams/createTask-columns.txt", "link").forEach { put("task.link", it) }
        column("repository/teams/createTask-columns.txt", "sync").forEach { put("task.sync", it) }
        column("repository/ratings/local-user-column.txt", "user", " rate=").forEach { put("rating.user.local", it) }
        column("repository/ratings/synced-user-column.txt", "user", " rate=").forEach { put("rating.user.synced", it) }
        column("repository/submissions/synced-columns.txt", "user").forEach { put("submission.user", it) }
        column("repository/submissions/synced-columns.txt", "parent").forEach { put("submission.parent", it) }
        listOf("user", "images", "viewIn", "conversations", "concatenatedLinks").forEach { key ->
            column("repository/voices/synced-columns.txt", key).forEachIndexed { i, value -> put("news.$key.$i", value) }
        }
    }

    @Test
    fun `kotlinx parses every Gson-written persisted string to the same tree`() {
        val samples = persistedSamples()
        assertTrue("expected the golden corpus, got ${samples.size}", samples.size >= 30)
        for ((name, text) in samples) {
            val viaGson = JsonParser.parseString(text).toKotlinx()
            val viaKotlinx = Json.parseToJsonElement(text)
            assertEquals(name, viaGson, viaKotlinx)
            assertEquals(name, viaGson, appJson.parseToJsonElement(text))
        }
        // The nested, doubly-encoded conversations string inside a shared-chat news payload.
        val innerText = Json.parseToJsonElement(samples.getValue("chatShare.news")).jsonObject.getValue("conversations").jsonPrimitive.content
        assertEquals(JsonParser.parseString(innerText).toKotlinx(), Json.parseToJsonElement(innerText))
    }

    @Serializable
    private data class SavedUser(
        val fullName: String? = null,
        val name: String? = null,
        val password: String? = null,
        val image: String? = null,
        val source: String? = null,
    )

    @Test
    fun `saved users written by Gson decode with the app kotlinx Json`() {
        val stored = golden("services/savedUsers.txt")
        val viaGson: List<User> = NetworkModule.provideGson().fromJson(stored, object : TypeToken<List<User>>() {}.type)
        val viaKotlinx = appJson.decodeFromString(ListSerializer(SavedUser.serializer()), stored)
        assertEquals(viaGson.map { listOf(it.fullName, it.name, it.password, it.image, it.source) }, viaKotlinx.map { listOf(it.fullName, it.name, it.password, it.image, it.source) })
        assertTrue(viaKotlinx.first().password!!.contains("=<word>&'"))
    }

    @Test
    fun `conversations and links written by Gson decode with kotlinx`() {
        val conversations = appJson.decodeFromString(ListSerializer(Conversation.serializer()), golden("model/persisted/News-createNews-conversations.txt"))
        val viaGson = Gson().fromJson(golden("model/persisted/News-createNews-conversations.txt"), Array<Conversation>::class.java)
        assertEquals(viaGson.map { it.query to it.response }, conversations.map { it.query to it.response })
        assertEquals("<b>4</b> & that's it", conversations.first().response)

        // ConfigurationsRepositoryImpl.getQueuedDownloads already reads this Gson-written preference with kotlinx.
        val links = column("repository/voices/synced-columns.txt", "concatenatedLinks").single()
        assertEquals(listOf("https://old.example/a?b=1&c=<2>"), Json.decodeFromString(ListSerializer(String.serializer()), links))
    }

    @Test
    fun `cached version detail written by Gson decodes with kotlinx`() {
        val planet = MyPlanet().apply {
            planetVersion = "0.20.1 <beta>"
            latestapkcode = 7064
            apkpath = "https://x.org/a?b=1&c=2"
        }
        val stored = Gson().toJson(planet)
        val decoded = appJson.decodeFromString(MyPlanet.serializer(), stored)
        assertEquals(listOf(planet.planetVersion, planet.minapkcode, planet.latestapkcode, planet.apkpath, planet.appname, planet.localapkpath),
            listOf(decoded.planetVersion, decoded.minapkcode, decoded.latestapkcode, decoded.apkpath, decoded.appname, decoded.localapkpath))
    }

    private fun outcome(block: () -> Any?): String = try {
        when (val result = block()) {
            is Array<*> -> result.toList().toString()
            else -> result.toString()
        }
    } catch (e: Exception) {
        "throws ${e::class.java.simpleName}"
    }

    @Test
    fun `reflective Gson fromJson versus kotlinx decoding on mistyped input`() {
        val gson = NetworkModule.provideGson()
        val userListType = object : TypeToken<List<User>>() {}.type
        val users = listOf(
            "[]", "[{}]", """[{"name":"a","extra":1}]""", """[{"name":5}]""", """[{"name":true}]""", """[{"name":{"x":1}}]""",
            "[null]", "null", "", """[{"name":"a",}]""", """{"name":"a"}""", """[{name:a}]""", """[{'name':'a'}]""",
        )
        val stepMistakes = listOf("""{"1":2}""", """{"1":"2"}""", """{"1":2.0}""", """{"1":2.5}""", """{"1":null}""", """{}""")
        val versions = listOf("\"0.70.64\"", "0.70.64", "7064", "")
        val table = GsonJsonObject()
        fun row(group: String, input: String, viaGson: String, viaKotlinx: String) {
            val rows = table.getAsJsonObject(group) ?: GsonJsonObject().also { table.add(group, it) }
            rows.add(input.ifEmpty { "<empty>" }, GsonJsonObject().apply {
                addProperty("gson", viaGson)
                addProperty("kotlinx", viaKotlinx)
            })
        }
        for (input in users) {
            row(
                "savedUsers", input,
                outcome { gson.fromJson<List<User>>(input, userListType)?.map { it.name } },
                outcome { appJson.decodeFromString(ListSerializer(SavedUser.serializer()), input).map { it.name } },
            )
        }
        for (input in stepMistakes) {
            row(
                "stepMistake", input,
                outcome { Gson().fromJson<Map<String, Int>>(input, object : TypeToken<Map<String, Int>>() {}.type) },
                outcome { appJson.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), input) },
            )
        }
        for (input in versions) {
            row(
                "apkVersionString", input,
                outcome { Gson().fromJson(input, String::class.java) },
                outcome { appJson.decodeFromString(String.serializer(), input) },
            )
        }
        assertMatchesGolden("utils/fromjson-vs-kotlinx", table)
    }
}
