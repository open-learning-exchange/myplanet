@file:OptIn(ExperimentalCoroutinesApi::class)

package org.ole.planet.myplanet.ui.userprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.UserProfileDbHandler
import org.mockito.kotlin.any
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmUserModel

@RunWith(MockitoJUnitRunner::class)
class EditAchievementFragmentTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var databaseService: DatabaseService

    @Mock
    private lateinit var profileDbHandler: UserProfileDbHandler

    @Mock
    private lateinit var inflater: LayoutInflater

    @Mock
    private lateinit var container: ViewGroup

    @Mock
    private lateinit var savedInstanceState: Bundle

    private lateinit var fragment: EditAchievementFragment

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fragment = EditAchievementFragment().apply {
            this.databaseService = this@EditAchievementFragmentTest.databaseService
            this.profileDbHandler = this@EditAchievementFragmentTest.profileDbHandler
            this.user = RealmUserModel()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onCreateView calls initializeData which calls withRealmAsync`() = runTest {
        fragment.onCreateView(inflater, container, savedInstanceState)
        verify(databaseService).withRealmAsync<RealmAchievement>(any())
    }
}
