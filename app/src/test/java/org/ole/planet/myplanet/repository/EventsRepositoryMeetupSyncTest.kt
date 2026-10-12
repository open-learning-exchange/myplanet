package org.ole.planet.myplanet.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.utils.SystemTimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real in-memory Room coverage for [EventsRepositoryImpl.insertMeetupsFromSync]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EventsRepositoryMeetupSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var meetupDao: MeetupDao
    private lateinit var repository: EventsRepositoryImpl

    private fun pulledMeetup(remoteId: String, title: String) = JsonObject().apply {
        addProperty("_id", remoteId)
        addProperty("_rev", "2-abc")
        addProperty("title", title)
        add("link", JsonObject().apply { addProperty("teams", "team1") })
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        meetupDao = db.meetupDao()
        repository = EventsRepositoryImpl(SystemTimeProvider(), meetupDao, Gson())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `pulling an uploaded local meetup updates it instead of adding a second row`() = runBlocking {
        // Created here, then uploaded: local UUID id, server id in meetupId
        meetupDao.upsert(Meetup().apply { id = "local-uuid"; meetupId = "server-1"; title = "Old" })

        repository.insertMeetupsFromSync(listOf(pulledMeetup("server-1", "New")))

        val rows = meetupDao.getByMeetupIds(listOf("server-1"))
        assertEquals(listOf("local-uuid"), rows.map { it.id })
        assertEquals("New", rows[0].title)
    }

    @Test
    fun `pulling removes a duplicate already stored under the server id`() = runBlocking {
        meetupDao.upsert(Meetup().apply { id = "local-uuid"; meetupId = "server-1"; title = "Meetup" })
        meetupDao.upsert(Meetup().apply { id = "server-1"; meetupId = "server-1"; title = "Meetup" })

        repository.insertMeetupsFromSync(listOf(pulledMeetup("server-1", "Meetup")))

        assertEquals(listOf("local-uuid"), meetupDao.getByMeetupIds(listOf("server-1")).map { it.id })
    }

    @Test
    fun `meetups created on the server are stored under their server id as before`() = runBlocking {
        repository.insertMeetupsFromSync(listOf(pulledMeetup("server-2", "From Planet")))

        assertEquals(listOf("server-2"), meetupDao.getByMeetupIds(listOf("server-2")).map { it.id })
    }
}
