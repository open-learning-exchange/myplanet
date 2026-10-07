package org.ole.planet.myplanet.ui.user

import com.google.gson.JsonArray
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray as KJsonArray
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ProfileFieldsUpdate
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY_URL
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden

/** Pins the achievements and references the edit screen hands the repository to save. */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementEntriesGoldenTest {
    private val userRepository = mockk<UserRepository>()
    private val resourcesRepository = mockk<ResourcesRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userRepository.achievementUpdates } returns flowOf()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `edited entries reach the repository unchanged`() = runTest(testDispatcher) {
        val stored = Achievement().apply {
            achievements = listOf(
                """{"title":"Old","description":"<d>","date":"2020-01-01","link":"","resources":[{"_id":"r1","size":1.50,"exp":1e2,"big":9007199254740993,"neg":-0}]}""",
                """{"title":"Lenient",'description':'single',link:unquoted,"resources":[]}""",
                "\"plain\"",
                "42.0",
                "",
            )
            references = listOf(
                """{"name":"Ref","phone":5551234,"relationship":"r","email":null,"rating":4.50}""",
            )
        }
        val library = MyLibrary().apply {
            _id = "lib1"
            _rev = "1-a"
            title = TRICKY
            averageRating = "4.5"
            timesRated = 3
            sum = 12
            subject = listOf("math", "<sci>")
            resourceLocalAddress = "file.pdf"
        }

        val achievements = AchievementEntries.loaded(stored.achievementsArray)!!
        achievements.add(AchievementEntries.entry("Desc $TRICKY", "Title", "2024-01-02", TRICKY_URL, AchievementEntries.resources(listOf(library))))
        achievements.add(AchievementEntries.entry("", "No resources", "", "", AchievementEntries.resources(emptyList())))
        achievements.add(AchievementEntries.entry("", "Null resources", "", "", null))
        val references = AchievementEntries.loaded(stored.getReferencesArray())!!
        references.add(AchievementEntries.reference(TRICKY, "friend", "+1 555", "a@b.c"))

        val savedAchievements = slot<KJsonArray>()
        val savedReferences = slot<KJsonArray>()
        coEvery {
            userRepository.updateAchievement(any(), any(), any(), any(), any(), capture(savedAchievements), capture(savedReferences), any(), any(), any(), any())
        } returns Unit
        coEvery { userRepository.updateProfileFields(any(), any()) } returns Unit
        val viewModel = AchievementViewModel(userRepository, userRepository, resourcesRepository)

        viewModel.saveAchievement(request(achievements, references))

        assertMatchesGolden("ui/user/editAchievement-achievements", savedAchievements.captured)
        assertMatchesGolden("ui/user/editAchievement-references", savedReferences.captured)
    }

    @Test
    fun `nothing stored loads nothing`() {
        assertNull(AchievementEntries.loaded(null))
    }

    private fun request(achievements: JsonArray, references: JsonArray) = AchievementSaveRequest(
        achievementId = "user1@planet1",
        header = "header",
        goals = "goals",
        purpose = "purpose",
        sendToNation = "true",
        achievements = achievements,
        references = references,
        createdOn = "planet1",
        username = "user1",
        parentCode = "parent",
        resumeFileName = "",
        profileFields = ProfileFieldsUpdate(),
    )
}
