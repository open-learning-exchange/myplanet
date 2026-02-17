package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.robolectric.RobolectricTestRunner
import io.realm.RealmQuery

@RunWith(RobolectricTestRunner::class)
class TeamsRepositoryTest {

    private lateinit var teamsRepository: TeamsRepositoryImpl
    private lateinit var databaseService: DatabaseService
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var uploadManager: UploadManager
    private lateinit var gson: Gson
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var serverUrlMapper: ServerUrlMapper

    @Before
    fun setUp() {
        databaseService = mockk(relaxed = true)
        userSessionManager = mockk(relaxed = true)
        uploadManager = mockk(relaxed = true)
        gson = Gson()
        sharedPreferences = mockk(relaxed = true)
        serverUrlMapper = mockk(relaxed = true)

        // We use spyk to mock protected methods of the class under test
        val realRepository = TeamsRepositoryImpl(
            databaseService,
            userSessionManager,
            uploadManager,
            gson,
            sharedPreferences,
            serverUrlMapper
        )
        teamsRepository = spyk(realRepository, recordPrivateCalls = true)
    }

    @Test
    fun `isMember returns false when userId is null`() = runTest {
        val isMember = teamsRepository.isMember(null, "teamId")
        assertFalse(isMember)
    }

    @Test
    fun `isMember returns true when count is greater than 0`() = runTest {
        val userId = "user1"
        val teamId = "team1"

        // Mock the protected count method
        coEvery {
            teamsRepository["count"](RealmMyTeam::class.java, any<Boolean>(), any<RealmQuery<RealmMyTeam>.() -> Unit>())
        } returns 1L

        val isMember = teamsRepository.isMember(userId, teamId)
        assertTrue(isMember)
    }

    @Test
    fun `isMember returns false when count is 0`() = runTest {
        val userId = "user1"
        val teamId = "team1"

        coEvery {
            teamsRepository["count"](RealmMyTeam::class.java, any<Boolean>(), any<RealmQuery<RealmMyTeam>.() -> Unit>())
        } returns 0L

        val isMember = teamsRepository.isMember(userId, teamId)
        assertFalse(isMember)
    }
}
