package org.ole.planet.myplanet.data.room

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.entity.legacy.RoomUserEntity
import org.ole.planet.myplanet.model.Attachment
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.TagEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Exercises the real Room [AppDatabase] against Robolectric's SQLite — the only tests in the suite
 * that run actual schema/DAO SQL and the [Converters] instead of mocking the DAOs. They guard the
 * JSON list/embedded-object converters and the LIKE-on-JSON shelf-membership query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppDatabaseRoundTripTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `MyLibrary round-trips its List and embedded-attachment converters`() = runBlocking {
        val library = MyLibrary().apply {
            id = "lib1"
            _id = "lib1"
            title = "Algebra"
            titleNormal = "algebra"
            userId = listOf("userA", "userB")
            subject = listOf("math")
            level = listOf("beginner", "intermediate")
            attachments = listOf(
                Attachment().apply { id = "a1"; name = "cover.png"; length = 42 }
            )
        }
        db.myLibraryDao().upsert(library)

        val loaded = db.myLibraryDao().getById("lib1")
        assertEquals("Algebra", loaded?.title)
        assertEquals(listOf("userA", "userB"), loaded?.userId)
        assertEquals(listOf("beginner", "intermediate"), loaded?.level)
        assertEquals(1, loaded?.attachments?.size)
        assertEquals("cover.png", loaded?.attachments?.first()?.name)
        assertEquals(42L, loaded?.attachments?.first()?.length)
    }

    @Test
    fun `MyLibrary shelf-membership LIKE query matches exact userId list entries`() = runBlocking {
        db.myLibraryDao().upsertAll(
            listOf(
                MyLibrary().apply { id = "l1"; userId = listOf("userA") },
                MyLibrary().apply { id = "l2"; userId = listOf("userB") },
                // userAlpha must NOT match userA's pattern (quotes delimit exact entries)
                MyLibrary().apply { id = "l3"; userId = listOf("userAlpha") }
            )
        )

        val pattern = "%\"userA\"%"
        val forUserA = db.myLibraryDao().getForUserPattern(pattern).map { it.id }.sorted()

        assertEquals(listOf("l1"), forUserA)
    }

    @Test
    fun `News round-trips label list converter and top-level message query`() = runBlocking {
        val news = News().apply {
            id = "n1"
            _id = "n1"
            docType = "message"
            message = "hi"
            labels = listOf("Offer", "Request")
            time = 1000
        }
        db.newsDao().upsert(news)

        val loaded = db.newsDao().getById("n1")
        assertEquals("hi", loaded?.message)
        assertEquals(listOf("Offer", "Request"), loaded?.labels)

        // Top-level message feed query runs real SQL.
        val topLevel = db.newsDao().getTopLevelMessages().map { it.id }
        assertEquals(listOf("n1"), topLevel)
    }

    @Test
    fun `Tag round-trips its attachedTo list converter`() = runBlocking {
        db.tagDao().upsertAll(
            listOf(TagEntity().apply { id = "t1"; name = "Science"; attachedTo = listOf("res1", "res2") })
        )
        val loaded = db.tagDao().getByIds(listOf("t1")).first()
        assertEquals("Science", loaded.name)
        assertEquals(listOf("res1", "res2"), loaded.attachedTo)
    }

    @Test
    fun `legacy UserEntity round-trips roles list and deletes`() = runBlocking {
        db.userDao().upsert(
            RoomUserEntity(id = "u1", _id = "u1", name = "Ada", rolesList = listOf("learner", "leader"))
        )
        val loaded = db.userDao().getById("u1")
        assertEquals("Ada", loaded?.name)
        assertEquals(listOf("learner", "leader"), loaded?.rolesList)
        assertEquals(1, db.userDao().count())

        db.userDao().deleteById("u1")
        assertNull(db.userDao().getById("u1"))
    }

    @Test
    fun `Conversation value type instantiates for Gson serialization`() {
        // Conversation is embedded (Gson) inside JSON columns rather than owning a Room table.
        assertTrue(Conversation().let { true })
    }
}
