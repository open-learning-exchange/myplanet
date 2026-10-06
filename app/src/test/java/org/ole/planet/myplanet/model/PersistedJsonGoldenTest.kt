package org.ole.planet.myplanet.model

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.serialization.json.jsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.toKotlinx

/**
 * Golden snapshots of JSON text the models write into Room columns and preferences.
 *
 * These are raw (byte-exact) goldens: the text is stored and later either matched with SQL
 * `LIKE` (`news.viewIn` against `"_id":"<team>"` / `"section":"community"`,
 * `certification.courseIds`) or post-processed as text (`ChatSharePayload` -> `News.createNews`
 * replaces every `=` with `:` before parsing, which only works because Gson escapes `=` as
 * `\u003d`). Gson's HTML escaping (`<` -> `\u003c`, `=` -> `\u003d`, `'` -> `\u0027`, ...) is
 * the expected diff when one of these producers moves to kotlinx.
 */
class PersistedJsonGoldenTest {

    private var originalContext: Context? = null

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        originalContext = try {
            MainApplication.context
        } catch (_: Exception) {
            null
        }
        MainApplication.testContext = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        MainApplication.testContext = originalContext
        unmockkAll()
    }

    private fun raw(name: String, actual: String?) = assertMatchesRawGolden("model/persisted/$name", actual ?: "<null>")

    private fun user() = UserEntity(
        id = "org.couchdb.user:learner",
        _id = "org.couchdb.user:learner",
        _rev = "1-u",
        name = "learner",
        rolesList = listOf("learner"),
        firstName = TRICKY,
        lastName = null,
        planetCode = "planet",
        parentCode = "parent",
    )

    @Test
    fun `News getViewInJson`() {
        val map = hashMapOf<String?, String>("viewInId" to "team_1%", "viewInSection" to "teams", "name" to "Team <A> & 'B'")
        raw("News-viewIn-team", News.getViewInJson(map))
        raw("News-viewIn-empty", News.getViewInJson(hashMapOf()))
    }

    @Test
    fun `ChatSharePayload buildShareMap and its createNews round trip`() {
        val chat = ChatHistory().apply {
            _id = "chat-1"
            _rev = "2-c"
            title = "  Title = <x> & 'y'  "
            user = "learner"
            aiProvider = "openai"
            conversations = listOf(
                Conversation().apply {
                    query = "what is 2+2=?"
                    response = "<b>4</b> & that's it"
                },
                Conversation(),
            )
        }
        val team = TeamSummary("team-1", "Team", "local", "planet", 1L, "team", "active", null, null, null, null)
        val map = ChatSharePayload.buildShareMap(chat, "note <1>", team, "teams", 1_717_171_717_171L)

        raw("ChatSharePayload-news", map["news"])
        assertMatchesGolden("model/persisted/ChatSharePayload-map", JsonObject().apply { map.toSortedMap(compareBy { it }).forEach { (k, v) -> addProperty(k, v) } })

        // createNews rewrites every '=' to ':' before parsing map["news"]; Gson's = escaping keeps '=' in values intact.
        val news = News.createNews(map, user(), listOf("""{"imageUrl":"a=b"}"""))
        assertEquals("Title = <x> & 'y'", news.newsTitle)
        raw("News-createNews-viewIn", news.viewIn)
        raw("News-createNews-user", news.user)
        raw("News-createNews-conversations", news.conversations)
        assertEquals("chat-1", news.newsId)
        assertEquals(1_717_171_717_171L, news.newsCreatedDate)
    }

    @Test
    fun `News createNews reply keeps the caller viewIn verbatim and serializes a null user as null`() {
        val news = News.createNews(hashMapOf("message" to "hi", "viewIn" to """[{"_id":"t"}]"""), null, null, isReply = true)
        raw("News-createNews-reply-viewIn", news.viewIn)
        raw("News-createNews-null-user", news.user)
    }

    @Test
    fun `Feedback setMessages`() {
        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("message", TRICKY)
                addProperty("time", "1717171717171")
                addProperty("user", "learner")
            })
            add(JsonObject().apply {
                addProperty("message", "reply")
                addProperty("time", 1.5f)
                add("user", null)
            })
        }
        raw("Feedback-messages", Feedback().apply { setMessages(messages.toKotlinx().jsonArray) }.messages)
        raw("Feedback-messages-null", Feedback().apply { setMessages(null) }.messages)
    }

    @Test
    fun `Achievement list setters`() {
        val items = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("title", "<won> & 'placed'")
                addProperty("n", 1)
                addProperty("f", 0.1f)
                add("missing", null)
            })
            add("plain = string")
            add(JsonObject().apply { addProperty("title", "<won> & 'placed'") })
        }
        val achievement = Achievement().apply {
            setAchievements(items.toKotlinx().jsonArray)
            setReferences(items.toKotlinx().jsonArray)
            setLinks(items.toKotlinx().jsonArray)
            setOtherInfo(null)
        }
        raw("Achievement-achievements", achievement.achievements!!.joinToString("\n"))
        raw("Achievement-references", achievement.references!!.joinToString("\n"))
        raw("Achievement-links", achievement.links!!.joinToString("\n"))
        assertEquals(emptyList<String>(), achievement.otherInfo)
    }

    @Test
    fun `Certification setCourseIds`() {
        raw("Certification-courseIds", Certification().apply {
            setCourseIds(JsonArray().apply {
                add("course_1")
                add("course<2>")
            }.toKotlinx().jsonArray)
        }.courseIds)
        raw("Certification-courseIds-null", Certification().apply { setCourseIds(null) }.courseIds)
    }

    @Test
    fun `MyCourse saveConcatenatedLinksToPrefs`() {
        val spm = mockk<SharedPrefManager>(relaxed = true)
        val saved = slot<String>()
        every { spm.getConcatenatedLinks() } returns """["https://a.org/x?y=1&z=<2>"]"""
        every { spm.setConcatenatedLinks(capture(saved)) } returns Unit
        MyCourse.saveConcatenatedLinksToPrefs(spm)
        val stored = saved.captured
        // The pending-link set is process-wide (other tests add to it, and adding here would leak into
        // MyCourseTest), so only pin the entry this test controls: the one read back from the preference.
        assertEquals(true, stored.startsWith("["))
        assertEquals(true, stored.contains("\"https://a.org/x?y\\u003d1\\u0026z\\u003d\\u003c2\\u003e\""))
    }
}
