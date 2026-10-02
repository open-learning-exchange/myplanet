package org.ole.planet.myplanet.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.NewsLogDao
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Real in-memory Room coverage for [VoicesRepositoryImpl.getNewsWithReplies], so the reply
 * ordering and filtering in `NewsDao.getReplies` is exercised rather than stubbed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VoicesRepositoryNewsWithRepliesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var newsDao: NewsDao
    private lateinit var repository: VoicesRepositoryImpl

    private fun news(id: String, replyTo: String? = null, time: Long = 0L) = News().apply {
        this.id = id
        _id = id
        this.replyTo = replyTo
        this.time = time
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        newsDao = db.newsDao()

        repository = VoicesRepositoryImpl(
            TestDispatcherProvider(mainDispatcherRule.testDispatcher),
            Gson(),
            Gson(),
            mockk<SharedPrefManager>(relaxed = true),
            newsDao,
            mockk<NewsLogDao>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getNewsWithReplies returns parent and only its replies newest first`() = runTest {
        newsDao.upsertAll(
            listOf(
                news("p1", time = 0L),
                news("r2", replyTo = "p1", time = 2L),
                news("r1", replyTo = "p1", time = 1L),
                news("r3", replyTo = "P1", time = 3L),
                news("other", replyTo = "p2", time = 4L),
            )
        )

        val (parent, replies) = repository.getNewsWithReplies("p1")

        assertEquals("p1", parent?.id)
        assertEquals(listOf("r3", "r2", "r1"), replies.map { it.id })
    }

    @Test
    fun `getNewsWithReplies returns null parent and no replies when news is missing`() = runTest {
        newsDao.upsertAll(listOf(news("p1"), news("r1", replyTo = "p1", time = 1L)))

        val (parent, replies) = repository.getNewsWithReplies("missing")

        assertNull(parent)
        assertEquals(emptyList<News>(), replies)
    }
}
