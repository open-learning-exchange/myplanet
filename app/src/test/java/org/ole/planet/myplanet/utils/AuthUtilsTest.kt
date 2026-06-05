package org.ole.planet.myplanet.utils

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ole.planet.myplanet.repository.UserRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AuthUtilsTest {

    private val userRepository = mockk<UserRepository>()

    @Test
    fun validateUsername_returnsValidString_whenUserRepositoryReturnsString() = runTest {
        val username = "valid_user"
        val expectedResult = "validated_string"
        coEvery { userRepository.validateUsername(username) } returns expectedResult

        val result = AuthUtils.validateUsername(username, userRepository)

        assertEquals(expectedResult, result)
    }

    @Test
    fun validateUsername_returnsNull_whenUserRepositoryReturnsNull() = runTest {
        val username = "invalid_user"
        coEvery { userRepository.validateUsername(username) } returns null

        val result = AuthUtils.validateUsername(username, userRepository)

        assertNull(result)
    }
}
