package org.ole.planet.myplanet.model

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.VersionUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toGsonAsParsed
import org.ole.planet.myplanet.utils.toKotlinx

/**
 * Goldens for the model JSON builders and readers the first golden pass left out, recorded
 * from the Gson-era code before the model package moved to kotlinx.serialization. Inputs and
 * outputs cross the same bridges the (still Gson) callers use, so each row pins what a caller
 * sees: `toGson()` for values that used to be built and bridged, `toGsonAsParsed()` for values
 * that used to be Gson parse results.
 */
class ModelGapGoldenTest {

    private var originalContext: Context? = null
    private lateinit var context: Context

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        every { VersionUtils.getVersionCode(any()) } returns 7064
        every { VersionUtils.getVersionName(any()) } returns "0.70.64"
        originalContext = try {
            MainApplication.context
        } catch (_: Exception) {
            null
        }
        context = mockk(relaxed = true)
        MainApplication.testContext = context
    }

    @After
    fun tearDown() {
        MainApplication.testContext = originalContext
        unmockkAll()
    }

    private fun golden(name: String, actual: Any?) = assertMatchesGolden("model/$name", actual)

    /** Pins a wall-clock field to 0 so the rest of the document can be a golden. */
    private fun pinTime(doc: Any?, key: String = "time"): JsonObject {
        val tree = Json.parseToJsonElement(JsonGolden.jsonText(doc)).jsonObject
        val value = tree[key] as? JsonPrimitive
        check(value != null && !value.isString && value.longOrNull != null) { "$key is not a long: $value" }
        return JsonObject(tree.mapValues { (k, v) -> if (k == key) JsonPrimitive(0L) else v })
    }

    private fun outcome(block: () -> Any?): String = try {
        when (val result = block()) {
            is com.google.gson.JsonElement -> JsonGolden.jsonText(result)
            is kotlinx.serialization.json.JsonElement -> JsonGolden.jsonText(result)
            is List<*> -> result.joinToString(prefix = "[", postfix = "]") { item ->
                when (item) {
                    is com.google.gson.JsonElement -> JsonGolden.jsonText(item)
                    is FeedbackReply -> "${item.message}|${item.user}|${item.date}"
                    else -> item.toString()
                }
            }
            else -> result.toString()
        }
    } catch (_: Exception) {
        // The exception type is library-specific (Gson vs kotlinx); none of these callers catch it.
        "throws"
    }

    @Test
    fun `CourseActivity serialize`() {
        val full = CourseActivity().apply {
            user = "learner"
            courseId = "course-1"
            type = "visit"
            title = TRICKY
            time = BIG_LONG
            createdOn = "planet"
            parentCode = "parent"
        }
        golden("CourseActivity-full", CourseActivity.serialize(full).toGson())
        golden("CourseActivity-sparse", CourseActivity.serialize(CourseActivity()).toGson())
    }

    @Test
    fun `SearchActivity serialize`() {
        val full = SearchActivity(
            id = "s1", text = TRICKY, type = "courses", time = BIG_LONG, user = "learner",
            filter = """{"tags":["a<b>"],"subjects":[],"levels":["x"],"n":1.0,"big":1e2,"nested":{"k":null}}""",
            createdOn = "planet", parentCode = "parent",
        )
        golden("SearchActivity-full", full.serialize("android-id", "custom <name>").toGson())
        golden("SearchActivity-sparse", SearchActivity().serialize(null, "").toGson())
    }

    @Test
    fun `TagEntity getTagsArray`() {
        val tags = listOf(TagEntity().apply { _id = "tag<1>" }, TagEntity(), TagEntity().apply { _id = "" })
        golden("TagEntity-tagsArray", TagEntity.getTagsArray(tags).toGson())
    }

    private fun usageStats(pkg: String, last: Long, first: Long, lastStamp: Long, fg: Long) = mockk<UsageStats>(relaxed = true) {
        every { packageName } returns pkg
        every { lastTimeUsed } returns last
        every { firstTimeStamp } returns first
        every { lastTimeStamp } returns lastStamp
        every { totalTimeInForeground } returns fg
    }

    private fun prefs(versionDetail: String?): SharedPrefManager = mockk(relaxed = true) {
        every { getVersionDetail() } returns versionDetail
        every { getLastSync() } returns BIG_LONG
        every { getLastUsageUploaded() } returns 1000L
    }

    @Test
    fun `MyPlanet activities`() {
        val usageStatsManager = mockk<UsageStatsManager>(relaxed = true)
        every { context.getSystemService(Context.USAGE_STATS_SERVICE) } returns usageStatsManager
        every { context.packageName } returns "org.ole.planet.myplanet"
        every { usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 1000L, 5000L) } returns listOf(
            usageStats("org.ole.planet.myplanet", 4000L, 2000L, 3000L, 1000L),
            usageStats("com.other", 1L, 1L, 1L, 1L),
            usageStats("org.ole.planet.myplanet", 0L, -1L, 7L, 0L),
        )
        val user = UserEntity(id = "u", parentCode = "parent<1>", planetCode = "planet&1")
        val versionDetail = """{"planetVersion":"0.20.1 <beta>","minapkcode":1,"latestapkcode":7064,"apkpath":"https://x.org/a?b=1&c=2"}"""

        golden("MyPlanet-tabletUsages", MyPlanet.getTabletUsages(context, prefs(versionDetail), now = 5000L).toGson())
        golden("MyPlanet-activities", MyPlanet.getMyPlanetActivities(context, prefs(versionDetail), user, now = 5000L).toGson())
        golden("MyPlanet-activities-no-version", MyPlanet.getMyPlanetActivities(context, prefs(null), user, now = 5000L).toGson())
        golden("MyPlanet-normalActivities", pinTime(MyPlanet.getNormalMyPlanetActivities(context, prefs(versionDetail), user).toGson()))
        golden("MyPlanet-normalActivities-empty-version", pinTime(MyPlanet.getNormalMyPlanetActivities(context, prefs(""), user).toGson()))
        golden("MyPlanet-normalActivities-json-null", pinTime(MyPlanet.getNormalMyPlanetActivities(context, prefs("null"), user).toGson()))
        golden("MyPlanet-normalActivities-unknown-keys", pinTime(MyPlanet.getNormalMyPlanetActivities(context, prefs("""{"planetVersion":"v<1>","extra":{"a":[1]},"localapkpath":null}"""), user).toGson()))
        golden("MyPlanet-normalActivities-null-planetVersion", pinTime(MyPlanet.getNormalMyPlanetActivities(context, prefs("""{"minapkcode":1}"""), user).toGson()))
    }

    @Test
    fun `Achievement fromJson stores each list item as Gson text`() {
        val doc = buildJsonObject {
            put("_id", "ach-1")
            put("achievements", Json.parseToJsonElement("""[{"title":"<won> & 'x'","n":1.50,"e":1e2,"z":-0,"l":$BIG_LONG,"nul":null},"s = t",{"title":"<won> & 'x'","n":1.50,"e":1e2,"z":-0,"l":$BIG_LONG,"nul":null},7,true,null]"""))
            put("references", Json.parseToJsonElement("""[{"name":"r","phone":"1"}]"""))
            put("links", Json.parseToJsonElement("""[]"""))
        }
        val achievement = Achievement.fromJson(doc.toGson().toKotlinx().jsonObject)
        assertMatchesRawGolden("model/persisted/Achievement-fromJson-achievements", achievement.achievements!!.joinToString("\n"))
        assertMatchesRawGolden("model/persisted/Achievement-fromJson-references", achievement.references!!.joinToString("\n"))
        assertMatchesRawGolden("model/persisted/Achievement-fromJson-links-otherInfo", "${achievement.links}|${achievement.otherInfo}")
    }

    @Test
    fun `model read side`() {
        val table = com.google.gson.JsonObject()
        fun row(name: String, block: () -> Any?) = table.addProperty(name, outcome(block))

        val images = listOf(null, "", "[]", """[{"imageUrl":"a<b>","n":1.0,"e":1e2}]""", "not json", "{}", """["x",1]""")
        images.forEachIndexed { i, s -> row("News.imagesArray[$i]") { News().apply { this.images = s }.imagesArray.toGsonAsParsed() } }
        val labels = listOf(null, emptyList(), listOf("a<b>", "", "c = d"))
        labels.forEachIndexed { i, l -> row("News.labelsArray[$i]") { News().apply { this.labels = l }.labelsArray.toGson() } }

        val viewIns = listOf(
            null, "", "[]", "not json",
            """[{"_id":"t","section":"teams"}]""",
            """[{"_id":"c","section":"Community","sharedDate":1717171717171}]""",
            """[{"section":"teams"},{"section":"community","sharedDate":"123"}]""",
            """[{"section":"community"}]""",
            """[{"section":"community","sharedDate":1e3}]""",
            """["x"]""",
        )
        viewIns.forEachIndexed { i, v ->
            row("News.isCommunityNews[$i]") { News().apply { viewIn = v; time = 42L }.isCommunityNews }
            row("News.calculateSortDate[$i]") { News().apply { viewIn = v; time = 42L }.calculateSortDate() }
        }

        val messages = listOf(
            null, "", "[]",
            """[{"message":"first <1>","time":"1","user":"a"},{"message":"reply","time":2,"user":"b"},{"message":"x","time":1.5,"user":"c"}]""",
            """[{"message":"only"}]""",
            """[{"message":1}]""",
            "not json",
        )
        messages.forEachIndexed { i, m ->
            row("Feedback.message[$i]") { Feedback().apply { this.messages = m }.message }
            row("Feedback.messageList[$i]") { Feedback().apply { this.messages = m }.messageList }
        }

        val conditions = listOf(null, "", "{}", """{"Asthma":true,"Diabetes":false,"Other <x>":"true","Flu":"TRUE","N":1}""", "not json", "[]")
        conditions.forEachIndexed { i, c -> row("HealthExamination.formatConditions[$i]") { HealthExamination.formatConditions(c) } }

        mockkObject(AndroidDecrypter.Companion)
        val decrypted = listOf(
            """{"notes":"<n> & 'm'","n":1.0,"e":1e2,"nested":{"a":null}}""", "", "null", "[]", "not json",
        )
        decrypted.forEachIndexed { i, d ->
            every { AndroidDecrypter.decrypt(any(), any(), any()) } returns d
            row("HealthExamination.getEncryptedDataAsJson[$i]") { HealthExamination().apply { data = "x" }.getEncryptedDataAsJson(UserEntity()).toGsonAsParsed() }
        }
        row("HealthExamination.getEncryptedDataAsJson[no data]") { HealthExamination().getEncryptedDataAsJson(UserEntity()).toGsonAsParsed() }

        val choices = listOf(null, emptyList(), listOf("""{"id":"a","text":"<A>","n":1.0}""", """{"b":1e2}"""), listOf(""))
        choices.forEachIndexed { i, c -> row("Answer.valueChoicesArray[$i]") { Answer(valueChoices = c).valueChoicesArray.toGson() } }

        val stored = listOf(null, emptyList(), listOf("""{"title":"<won>","n":1.0,"e":1e2}""", "\"plain\"", "7", "null"))
        stored.forEachIndexed { i, s ->
            row("Achievement.achievementsArray[$i]") { Achievement().apply { achievements = s }.achievementsArray.toGsonAsParsed() }
            row("Achievement.getReferencesArray[$i]") { Achievement().apply { references = s }.getReferencesArray().toGsonAsParsed() }
            row("Achievement.linksArray[$i]") { Achievement().apply { links = s }.linksArray.toGsonAsParsed() }
            row("Achievement.otherInfoArray[$i]") { Achievement().apply { otherInfo = s }.otherInfoArray.toGsonAsParsed() }
        }

        row("ExamQuestion.correctChoiceArray[null]") { ExamQuestion().correctChoiceArray }
        row("ExamQuestion.correctChoiceArray[full]") { ExamQuestion(correctChoiceList = listOf("a<b>", "")).correctChoiceArray }

        val revs = listOf(null, """{"_id":"x","_rev":"1-a"}""", """{"_id":5,"_rev":null}""", "{}")
        revs.forEachIndexed { i, r ->
            row("OfflineActivity.changeRev[$i]") {
                val doc = r?.let { Json.parseToJsonElement(it).jsonObject.toGson().toKotlinx().jsonObject }
                OfflineActivity().apply { _id = "old"; _rev = "old" }.apply { changeRev(doc) }.let { "${it._id}|${it._rev}" }
            }
        }

        val docs = listOf(null, "{}", """{"_attachments":{}}""", """{"_attachments":{"b.jpg":{},"a.png":{}}}""", """{"_attachments":"x"}""")
        docs.forEachIndexed { i, d ->
            val gsonDoc = d?.let { Json.parseToJsonElement(it).jsonObject.toGson().toKotlinx().jsonObject }
            row("UserEntity.addImageUrl[$i]") { UserEntity(id = "org.couchdb.user:u").apply { addImageUrl(gsonDoc) }.userImage }
            if (gsonDoc != null) row("MyTeam.getFirstAttachmentName[$i]") { MyTeam.getFirstAttachmentName(gsonDoc) }
        }

        assertMatchesGolden("model/read-side", table)
    }
}
