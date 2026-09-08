package org.ole.planet.myplanet.ui.user

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementViewModelTest {

    private lateinit var viewModel: AchievementViewModel
    private val userRepository = mockk<UserRepository>()
    private val resourcesRepository = mockk<ResourcesRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userRepository.achievementUpdates } returns flowOf()
        viewModel = AchievementViewModel(userRepository, resourcesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadUserAndAchievement exposes user and achievement`() = runTest(testDispatcher) {
        val user = UserEntity(id = "user1").apply { planetCode = "planet1" }
        val achievement = Achievement()
        coEvery { userRepository.getUserModel() } returns user
        coEvery { userRepository.initializeAchievement("user1@planet1") } returns achievement

        viewModel.loadUserAndAchievement()
        advanceUntilIdle()

        assertEquals("user1", viewModel.user.value?.id)
        assertEquals(achievement, viewModel.achievement.value)
        assertEquals("user1@planet1", viewModel.achievementId.value)
    }

    @Test
    fun `loadUserAndAchievement builds achievement id from user id and planetCode`() = runTest(testDispatcher) {
        val user = UserEntity(id = "abc").apply { planetCode = "xyz" }
        coEvery { userRepository.getUserModel() } returns user
        coEvery { userRepository.initializeAchievement("abc@xyz") } returns null

        viewModel.loadUserAndAchievement()
        advanceUntilIdle()

        assertEquals("abc@xyz", viewModel.achievementId.value)
        coVerify(exactly = 1) { userRepository.initializeAchievement("abc@xyz") }
    }

    @Test
    fun `loadUserAndAchievement handles null user`() = runTest(testDispatcher) {
        coEvery { userRepository.getUserModel() } returns null
        coEvery { userRepository.initializeAchievement(any()) } returns null

        viewModel.loadUserAndAchievement()
        advanceUntilIdle()

        assertNull(viewModel.user.value)
        assertNull(viewModel.achievement.value)
        assertNull(viewModel.achievementId.value)
    }

    @Test
    fun `saveAchievement updates achievement then profile fields`() = runTest(testDispatcher) {
        val user = UserEntity(id = "user1").apply { planetCode = "planet1" }
        coEvery { userRepository.getUserModel() } returns user
        coEvery { userRepository.initializeAchievement(any()) } returns null
        coEvery {
            userRepository.updateAchievement(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Unit
        coEvery { userRepository.updateProfileFields(any(), any()) } returns Unit

        viewModel.loadUserAndAchievement()
        advanceUntilIdle()

        val achievements = JsonArray()
        val references = JsonArray()
        val profileFields = JsonObject().apply { addProperty("firstName", "John") }

        viewModel.saveAchievement(
            AchievementSaveRequest(
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
                resumeFileName = "cv.pdf",
                profileFields = profileFields,
            )
        )

        coVerify(exactly = 1) {
            userRepository.updateAchievement(
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
                resumeFileName = "cv.pdf"
            )
        }
        coVerify(exactly = 1) { userRepository.updateProfileFields("user1", profileFields) }
    }

    @Test
    fun `getAllLibraries delegates to resourcesRepository`() = runTest(testDispatcher) {
        val libraries = listOf(MyLibrary().apply { id = "r1"; title = "Lib 1" })
        coEvery { resourcesRepository.getAllLibraries() } returns libraries

        val result = viewModel.getAllLibraries()

        assertEquals(1, result.size)
        assertEquals("Lib 1", result[0].title)
    }

    @Test
    fun `loadUserAndAchievement emits user and achievement once for one-shot consumers`() = runTest(testDispatcher) {
        val user = UserEntity(id = "user1").apply { planetCode = "planet1" }
        val achievement = Achievement()
        coEvery { userRepository.getUserModel() } returns user
        coEvery { userRepository.initializeAchievement("user1@planet1") } returns achievement

        viewModel.loadUserAndAchievement()
        advanceUntilIdle()

        val firstUser = viewModel.user.filterNotNull().first()
        val firstAchievement = viewModel.achievement.filterNotNull().first()

        assertEquals("user1", firstUser.id)
        assertEquals(achievement, firstAchievement)
        assertEquals(firstAchievement, viewModel.achievement.value)
    }
}
