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
import org.ole.planet.myplanet.model.News
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
        .addMigrations(AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22)
        .fallbackToDestructiveMigration(true)
        .allowMainThreadQueries()
        .build()

    @Test
    fun `in-place migration ends at the current database version`() {
        assertEquals(AppDatabase.VERSION, AppDatabase.MIGRATION_21_22.endVersion)
        assertEquals(AppDatabase.MIGRATION_20_21.endVersion, AppDatabase.MIGRATION_21_22.startVersion)
    }

    @Test
    fun `upgrading from 20 adds the my_library indices and keeps unsynced rows`() = runBlocking {
        build().apply {
            myLibraryDao().upsertAll(listOf(MyLibrary().apply { id = "pending"; title = "Not uploaded yet" }))
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            listOf("courseId", "stepId", "resourceLocalAddress").forEach {
                raw.execSQL("DROP INDEX `index_my_library_$it`")
            }
            raw.execSQL("ALTER TABLE `news` DROP COLUMN `videoUrls`")
            raw.execSQL("ALTER TABLE `news` DROP COLUMN `videos`")
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

    @Test
    fun `upgrading from 21 adds the news video columns and keeps unsynced rows`() = runBlocking {
        build().apply {
            newsDao().upsert(News().apply { id = "pending"; message = "Not uploaded yet" })
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("ALTER TABLE `news` DROP COLUMN `videoUrls`")
            raw.execSQL("ALTER TABLE `news` DROP COLUMN `videos`")
            raw.version = 21
        }

        val db = build()
        val columns = db.openHelper.readableDatabase
            .query("PRAGMA table_info(`news`)")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
        val row = db.newsDao().getById("pending")
        db.close()

        assertEquals("Not uploaded yet", row?.message)
        assertTrue(columns.containsAll(listOf("videoUrls", "videos")))
    }
}
