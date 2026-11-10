package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.TagEntity

@RunWith(AndroidJUnit4::class)
class TagDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var tagDao: TagDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        tagDao = database.tagDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun getByDbAndLinkIds_returnsAllRowsWhenExceedingChunkLimit() = runBlocking {
        val tags = (1..1200).map { i ->
            TagEntity().apply {
                id = "tag_link_$i"
                db = "resources"
                linkId = "link_$i"
            }
        }
        tagDao.upsertAll(tags)

        val linkIds = (1..1200).map { "link_$it" }
        val result = tagDao.getByDbAndLinkIds("resources", linkIds)

        assertEquals(1200, result.size)
    }

    @Test
    fun getByIds_returnsAllRowsWhenExceedingChunkLimit() = runBlocking {
        val tags = (1..1200).map { i ->
            TagEntity().apply {
                id = "tag_id_$i"
                name = "Tag $i"
            }
        }
        tagDao.upsertAll(tags)

        val ids = (1..1200).map { "tag_id_$it" }
        val result = tagDao.getByIds(ids)

        assertEquals(1200, result.size)
    }

    @Test
    fun getByDbAndLinkIds_returnsEmptyWhenInputIsEmpty() = runBlocking {
        val tag = TagEntity().apply {
            id = "tag_1"
            db = "resources"
            linkId = "link_1"
        }
        tagDao.upsertAll(listOf(tag))

        val result = tagDao.getByDbAndLinkIds("resources", emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun getByIds_returnsEmptyWhenInputIsEmpty() = runBlocking {
        val tag = TagEntity().apply {
            id = "tag_1"
            name = "Tag 1"
        }
        tagDao.upsertAll(listOf(tag))

        val result = tagDao.getByIds(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun getByDbAndLinkIds_returnsNoDuplicateRowsWhenInputHasDuplicates() = runBlocking {
        val tag1 = TagEntity().apply {
            id = "tag_1"
            db = "resources"
            linkId = "link_1"
        }
        val tag2 = TagEntity().apply {
            id = "tag_2"
            db = "resources"
            linkId = "link_2"
        }
        tagDao.upsertAll(listOf(tag1, tag2))

        val result = tagDao.getByDbAndLinkIds("resources", listOf("link_1", "link_1", "link_2", "link_2"))
        assertEquals(2, result.size)
    }

    @Test
    fun getByIds_returnsNoDuplicateRowsWhenInputHasDuplicates() = runBlocking {
        val tag1 = TagEntity().apply {
            id = "tag_1"
            name = "Tag 1"
        }
        val tag2 = TagEntity().apply {
            id = "tag_2"
            name = "Tag 2"
        }
        tagDao.upsertAll(listOf(tag1, tag2))

        val result = tagDao.getByIds(listOf("tag_1", "tag_1", "tag_2", "tag_2"))
        assertEquals(2, result.size)
    }
}
