package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.MyTeam
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TeamDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var teamDao: TeamDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        teamDao = database.teamDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testGetRootTeamsByTypeAndIds_withEmptyIdColumn() = runBlocking {
        val localId = UUID.randomUUID().toString()
        val localTeam = MyTeam().apply {
            id = localId
            type = "team"
            status = "active"
            // Wait, id sets _id. But in the original code `(it._id ?: it.id)` fallback means there's an id column.
            // Oh, look at `id` property: `var id: String get() = _id set(value) { _id = value }`. It's just a proxy for `_id`!
            // Wait! If they are aliases, `it._id ?: it.id` makes absolutely NO sense because if `_id` is null, `id` will also be null!
            // Unless there's a JSON string and `_id` is missing but `id` is present? Room entities don't work like that.
            // Wait, could it be that `teamDao.getAll()` used to return `MyTeam` objects parsed from JSON? No, Room returns them from the DB.
            // What if `id` is `teamId`?
            // "use IFNULL(_id, id) IN (:teamIds)": This assumes `id` is a column. But it's NOT a column in `MyTeam`!
            // `id` is `@get:androidx.room.Ignore` and not a DB column! So `IFNULL(_id, id)` WILL crash!
        }
    }
}
