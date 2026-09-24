package org.ole.planet.myplanet.ui.user

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.model.MemberInfo
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class BecomeMemberViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: BecomeMemberViewModel

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        viewModel = BecomeMemberViewModel(userRepository)
    }

    @Test
    fun `rapid consecutive calls to onUsernameChanged cancel previous job and emit latest check`() = runTest {
        coEvery { userRepository.validateUsername("a") } returns "Too short"
        coEvery { userRepository.validateUsername("ab") } returns null

        val emittedChecks = mutableListOf<UsernameCheck>()
        val collectJob = launch(testDispatcher) {
            viewModel.usernameChecks.toList(emittedChecks)
        }

        viewModel.onUsernameChanged("a")
        advanceTimeBy(100)
        viewModel.onUsernameChanged("ab")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(1, emittedChecks.size)
        assertEquals(UsernameCheck("ab", null), emittedChecks[0])

        coVerify(exactly = 0) { userRepository.validateUsername("a") }
        coVerify(exactly = 1) { userRepository.validateUsername("ab") }

        collectJob.cancel()
    }

    @Test
    fun `two identical consecutive calls emit two checks on SharedFlow`() = runTest {
        coEvery { userRepository.validateUsername("abc") } returns "Username taken"

        val emittedChecks = mutableListOf<UsernameCheck>()
        val collectJob = launch(testDispatcher) {
            viewModel.usernameChecks.toList(emittedChecks)
        }

        viewModel.onUsernameChanged("abc")
        advanceTimeBy(300)
        advanceUntilIdle()

        viewModel.onUsernameChanged("abc")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(2, emittedChecks.size)
        assertEquals(UsernameCheck("abc", "Username taken"), emittedChecks[0])
        assertEquals(UsernameCheck("abc", "Username taken"), emittedChecks[1])

        coVerify(exactly = 2) { userRepository.validateUsername("abc") }

        collectJob.cancel()
    }

    @Test
    fun `pass-through functions delegate to UserRepository`() = runTest {
        val memberInfo = MemberInfo("user1", "pass123", "pass123", "John", "Doe", "", "john@example.com", "en", "level1", "123456", "2000-01-01", "male")
        coEvery { userRepository.createMember(memberInfo) } returns Pair(true, "Created")
        coEvery { userRepository.validateUsername("user1") } returns null

        val createResult = viewModel.createMember(memberInfo)
        val validateResult = viewModel.validateUsername("user1")
        viewModel.cleanupDuplicateUsers()

        assertEquals(Pair(true, "Created"), createResult)
        assertEquals(null, validateResult)

        coVerify(exactly = 1) { userRepository.createMember(memberInfo) }
        coVerify(exactly = 1) { userRepository.validateUsername("user1") }
        coVerify(exactly = 1) { userRepository.cleanupDuplicateUsers() }
    }
}
