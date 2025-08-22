package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.junit.Assert.assertEquals

@RunWith(MockitoJUnitRunner::class)
class UserRepositoryTest {

    @Mock
    private lateinit var mockDatabaseService: DatabaseService

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        userRepository = UserRepositoryImpl(mockDatabaseService, mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
    }

    @Test
    fun `test get user profile`() = runTest {
        // Arrange
        val expectedProfile = "{\"name\":\"test_user\"}"
        whenever(mockSharedPreferences.getString("user_profile", null)).thenReturn(expectedProfile)

        // Act
        val actualProfile = userRepository.getUserProfile()

        // Assert
        assertEquals(expectedProfile, actualProfile)
    }

    @Test
    fun `test save user data`() = runTest {
        // Arrange
        val dataToSave = "{\"name\":\"test_user_2\"}"
        whenever(mockEditor.putString("user_profile", dataToSave)).thenReturn(mockEditor)

        // Act
        userRepository.saveUserData(dataToSave)

        // Assert
        verify(mockEditor).putString("user_profile", dataToSave)
        verify(mockEditor).apply()
    }
}
