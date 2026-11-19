package org.ole.planet.myplanet.data.room

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.MyLibrary
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppDatabaseMigrationTest {

    private val context = RuntimeEnvironment.getApplication()
    private val dbName = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun build() = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(AppDatabase.MIGRATION_20_21)
        .fallbackToDestructiveMigration(true)
        .allowMainThreadQueries()
        .build()

    @Test
    fun `in-place migration ends at the current database version`() {
        // A later schema bump must either add the next migration in the chain or consciously
        // drop this one; either way this fails until someone looks. It cannot catch two branches
        // that both claim the same VERSION, so check master before bumping.
        assertEquals(AppDatabase.VERSION, AppDatabase.MIGRATION_20_21.endVersion)
    }

    @Test
    fun `upgrading from 20 adds the my_library indices and keeps unsynced rows`() = runBlocking {
        // Make a database that looks like version 20: the current schema minus the new indices.
        build().apply {
            myLibraryDao().upsertAll(listOf(MyLibrary().apply { id = "pending"; title = "Not uploaded yet" }))
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            listOf("courseId", "stepId", "resourceLocalAddress").forEach {
                raw.execSQL("DROP INDEX `index_my_library_$it`")
            }
            raw.version = 20
        }

        val db = build()
        val row = db.myLibraryDao().getById("pending")
        val indices = db.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'my_library'")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        db.close()

        assertEquals("Not uploaded yet", row?.title)
        assertTrue(indices.containsAll(listOf(
            "index_my_library_courseId",
            "index_my_library_stepId",
            "index_my_library_resourceLocalAddress"
        )))
    }
}
