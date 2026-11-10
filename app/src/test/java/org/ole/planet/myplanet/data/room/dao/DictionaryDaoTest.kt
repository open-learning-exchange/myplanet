package org.ole.planet.myplanet.data.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity

@RunWith(AndroidJUnit4::class)
class DictionaryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dictionaryDao: DictionaryDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dictionaryDao = database.dictionaryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun createEntity(
        id: String,
        word: String,
        meaning: String = "definition of $word"
    ) = DictionaryEntity(
        id = id,
        word = word,
        meaning = meaning
    )

    @Test
    fun count_returnsZeroOnEmptyTableAndThreeAfterInsertAllOfThreeEntries() = runBlocking {
        assertEquals(0L, dictionaryDao.count())

        val items = listOf(
            createEntity("1", "apple"),
            createEntity("2", "banana"),
            createEntity("3", "cherry")
        )
        dictionaryDao.insertAll(items)

        assertEquals(3L, dictionaryDao.count())
    }

    @Test
    fun findByWord_findsExactMatch() = runBlocking {
        val entity = createEntity("1", "apple", "A round red or green fruit")
        dictionaryDao.insertAll(listOf(entity))

        val result = dictionaryDao.findByWord("apple")
        assertNotNull(result)
        assertEquals("1", result?.id)
        assertEquals("apple", result?.word)
        assertEquals("A round red or green fruit", result?.meaning)
    }

    @Test
    fun findByWord_isCaseInsensitive() = runBlocking {
        val entity = createEntity("1", "Apple", "A round fruit")
        dictionaryDao.insertAll(listOf(entity))

        val lowerResult = dictionaryDao.findByWord("apple")
        assertNotNull(lowerResult)
        assertEquals("1", lowerResult?.id)
        assertEquals("Apple", lowerResult?.word)

        val upperResult = dictionaryDao.findByWord("APPLE")
        assertNotNull(upperResult)
        assertEquals("1", upperResult?.id)
        assertEquals("Apple", upperResult?.word)
    }

    @Test
    fun findByWord_returnsNullForMissingWord() = runBlocking {
        dictionaryDao.insertAll(listOf(createEntity("1", "apple")))

        val result = dictionaryDao.findByWord("orange")
        assertNull(result)
    }

    @Test
    fun insertAll_withDuplicateId_replacesRowAndUnchangedCount() = runBlocking {
        val initialEntity = createEntity("1", "apple", "Old meaning")
        dictionaryDao.insertAll(listOf(initialEntity))

        assertEquals(1L, dictionaryDao.count())
        assertEquals("Old meaning", dictionaryDao.findByWord("apple")?.meaning)

        val updatedEntity = createEntity("1", "apple", "New meaning")
        dictionaryDao.insertAll(listOf(updatedEntity))

        assertEquals(1L, dictionaryDao.count())
        val result = dictionaryDao.findByWord("apple")
        assertNotNull(result)
        assertEquals("New meaning", result?.meaning)
    }
}
