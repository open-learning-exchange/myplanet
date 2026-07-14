package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var gson: Gson
    private lateinit var repository: FeedbackRepositoryImpl
    private lateinit var mockRealm: Realm
    private lateinit var mockQuery: RealmQuery<RealmFeedback>
    private lateinit var mockResults: RealmResults<RealmFeedback>

    @Before
    fun setup() {
        databaseService = mockk()
        gson = Gson()
        mockRealm = mockk(relaxed = true)
        mockQuery = mockk(relaxed = true)
        mockResults = mockk(relaxed = true)

        coEvery { databaseService.withRealmAsync<List<RealmFeedback>>(any()) } answers {
            val block = firstArg<(Realm) -> List<RealmFeedback>>()
            block(mockRealm)
        }

        repository = FeedbackRepositoryImpl(
            databaseService = databaseService,
            legacyRealmDispatcher = Dispatchers.Unconfined,
            gson = gson
        )
    }

    @Test
    fun getPendingFeedback_returnsOnlyUnuploadedFeedback() = runTest {
        val feedback1 = RealmFeedback().apply { isUploaded = false }
        val feedback2 = RealmFeedback().apply { isUploaded = false }
        val expectedList = listOf(feedback1, feedback2)

        coEvery { mockRealm.where(RealmFeedback::class.java) } returns mockQuery
        coEvery { mockQuery.equalTo("isUploaded", false) } returns mockQuery
        coEvery { mockQuery.findAll() } returns mockResults
        coEvery { mockRealm.copyFromRealm(mockResults) } returns expectedList

        val result = repository.getPendingFeedback()

        io.mockk.coVerify {
            mockRealm.where(RealmFeedback::class.java)
            mockQuery.equalTo("isUploaded", false)
            mockQuery.findAll()
        }

        assertEquals(2, result.size)
        assertEquals(expectedList, result)
    }
}
