package org.ole.planet.myplanet.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@ExperimentalCoroutinesApi
class DictionaryRepositoryImplTest {

    private lateinit var dictionaryDao: DictionaryDao
    private lateinit var context: Context
    private lateinit var dispatcherProvider: TestDispatcherProvider
    private lateinit var dictionaryRepository: DictionaryRepositoryImpl
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setup() {
        dictionaryDao = mockk(relaxed = true)
        context = mockk(relaxed = true)
        dispatcherProvider = TestDispatcherProvider(testDispatcher)
        dictionaryRepository = DictionaryRepositoryImpl(dictionaryDao, dispatcherProvider, context)

        mockkObject(FileUtils)
    }

    @After
    fun teardown() {
        io.mockk.unmockkObject(FileUtils)
    }

    @Test
    fun `insertDictionaryData returns FileMissing if file does not exist`() = runTest(testDispatcher) {
        every { FileUtils.checkFileExist(context, Constants.DICTIONARY_URL) } returns false

        val result = dictionaryRepository.insertDictionaryData()

        assertTrue(result is DictionaryLoad.FileMissing)
        coVerify(exactly = 0) { dictionaryDao.insertAll(any()) }
    }

    @Test
    fun `insertDictionaryData returns AlreadyPopulated if data is already populated`() = runTest(testDispatcher) {
        every { FileUtils.checkFileExist(context, Constants.DICTIONARY_URL) } returns true
        coEvery { dictionaryDao.count() } returns 100L

        val result = dictionaryRepository.insertDictionaryData()

        assertTrue(result is DictionaryLoad.AlreadyPopulated)
        coVerify(exactly = 0) { dictionaryDao.insertAll(any()) }
    }

    @Test
    fun `insertDictionaryData returns Failed if json parsing fails`() = runTest(testDispatcher) {
        every { FileUtils.checkFileExist(context, Constants.DICTIONARY_URL) } returns true
        coEvery { dictionaryDao.count() } returns 0L
        every { FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL) } throws Exception("Forced exception for testing")

        val result = dictionaryRepository.insertDictionaryData()

        assertTrue(result is DictionaryLoad.Failed)
        coVerify(exactly = 0) { dictionaryDao.insertAll(any()) }
    }

    @Test
    fun `insertDictionaryData returns Inserted and inserts entities on success`() = runTest(testDispatcher) {
        every { FileUtils.checkFileExist(context, Constants.DICTIONARY_URL) } returns true
        coEvery { dictionaryDao.count() } returns 0L
        every { FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL) } returns mockk()

        val validJson = """[{"code": "1", "language": "en", "advance_code": "2", "word": "hello", "meaning": "greeting", "definition": "A greeting", "synonym": "hi", "antonoym": "bye"}]"""
        every { FileUtils.getStringFromFile(any()) } returns validJson

        val result = dictionaryRepository.insertDictionaryData()

        assertTrue(result is DictionaryLoad.Inserted)
        coVerify(exactly = 1) { dictionaryDao.insertAll(any()) }
    }

    @Test
    fun `concurrent insertDictionaryData calls only insert once`() = runTest(testScheduler) {
        every { FileUtils.checkFileExist(context, Constants.DICTIONARY_URL) } returns true
        every { FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL) } returns mockk()
        val validJson = """[{"code": "1", "language": "en", "advance_code": "2", "word": "hello", "meaning": "greeting", "definition": "A greeting", "synonym": "hi", "antonoym": "bye"}]"""
        every { FileUtils.getStringFromFile(any()) } returns validJson

        val concurrentDispatcher = StandardTestDispatcher(testScheduler)
        val concurrentRepository = DictionaryRepositoryImpl(
            dictionaryDao,
            TestDispatcherProvider(concurrentDispatcher),
            context
        )

        val inserted = AtomicBoolean(false)
        coEvery { dictionaryDao.count() } answers { if (inserted.get()) 1L else 0L }
        coEvery { dictionaryDao.insertAll(any()) } coAnswers {
            delay(10)
            inserted.set(true)
        }

        val results = listOf(
            async { concurrentRepository.insertDictionaryData() },
            async { concurrentRepository.insertDictionaryData() }
        ).map { it.await() }

        assertEquals(1, results.count { it is DictionaryLoad.Inserted })
        assertEquals(1, results.count { it is DictionaryLoad.AlreadyPopulated })
        coVerify(exactly = 1) { dictionaryDao.insertAll(any()) }
    }
}
