package org.ole.planet.myplanet.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.CrashLogStore
import org.ole.planet.myplanet.utils.VersionUtils

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var apkLogDao: ApkLogDao
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var repository: DiagnosticsRepositoryImpl

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Before
    fun setUp() {
        context = mockk()
        apkLogDao = mockk(relaxed = true)
        sharedPrefManager = mockk()
        userSessionManager = mockk()

        every { sharedPrefManager.getParentCode() } returns "parent-123"
        every { sharedPrefManager.getPlanetCode() } returns "planet-456"

        mockkObject(VersionUtils)
        every { VersionUtils.getVersionName(any()) } returns "0.63.42"

        repository = DiagnosticsRepositoryImpl(context, apkLogDao, sharedPrefManager, userSessionManager)
    }

    @Test
    fun `saveLogToRoom builds a log reusing buildApkLog fields`() = runTest {
        val user = UserEntity(id = "user-1")
        coEvery { userSessionManager.getUserModel() } returns user

        val inserted = slot<ApkLog>()
        coEvery { apkLogDao.insert(capture(inserted)) } returns Unit

        val result = repository.saveLogToRoom("crash", "boom", "1700000000000")

        assertTrue(result)
        val log = inserted.captured
        assertEquals("crash", log.type)
        assertEquals("boom", log.error)
        assertEquals("1700000000000", log.time)
        assertEquals("", log.page)
        assertEquals("parent-123", log.parentCode)
        assertEquals("planet-456", log.createdOn)
        assertEquals("0.63.42", log.version)
        assertEquals("user-1", log.userId)
        assertNotNull(log.id)
        assertTrue(log.id.isNotEmpty())
    }

    @Test
    fun `saveLogToRoom leaves userId null when no user is logged in`() = runTest {
        coEvery { userSessionManager.getUserModel() } returns null

        val inserted = slot<ApkLog>()
        coEvery { apkLogDao.insert(capture(inserted)) } returns Unit

        repository.saveLogToRoom("anr", "stuck", "1700000000000")

        assertNull(inserted.captured.userId)
    }

    @Test
    fun `saveLogToRoom returns false on exception`() = runTest {
        coEvery { userSessionManager.getUserModel() } throws RuntimeException("db down")

        val result = repository.saveLogToRoom("crash", "boom", "1700000000000")

        assertFalse(result)
        coVerify(exactly = 0) { apkLogDao.insert(any()) }
    }

    @Test
    fun `saveLogsToRoom returns true and inserts nothing for an empty list`() = runTest {
        val result = repository.saveLogsToRoom(emptyList())

        assertTrue(result)
        coVerify(exactly = 0) { apkLogDao.insertAll(any()) }
    }

    @Test
    fun `saveLogsToRoom builds each log reusing buildApkLog fields`() = runTest {
        val user = UserEntity(id = "user-1")
        coEvery { userSessionManager.getUserModel() } returns user

        val inserted = slot<List<ApkLog>>()
        coEvery { apkLogDao.insertAll(capture(inserted)) } returns Unit

        val pending = listOf(
            CrashLogStore.PendingLog(File("/tmp/a.log"), "crash", "1700000000001", "err1"),
            CrashLogStore.PendingLog(File("/tmp/b.log"), "anr", "1700000000002", "err2")
        )

        val result = repository.saveLogsToRoom(pending)

        assertTrue(result)
        val logs = inserted.captured
        assertEquals(2, logs.size)

        val first = logs[0]
        assertEquals("crash", first.type)
        assertEquals("err1", first.error)
        assertEquals("1700000000001", first.time)
        assertEquals("", first.page)
        assertEquals("parent-123", first.parentCode)
        assertEquals("planet-456", first.createdOn)
        assertEquals("0.63.42", first.version)
        assertEquals("user-1", first.userId)
        assertNotNull(first.id)
        assertTrue(first.id.isNotEmpty())

        val second = logs[1]
        assertEquals("anr", second.type)
        assertEquals("err2", second.error)
        assertEquals("1700000000002", second.time)
        assertEquals("user-1", second.userId)
        assertNotNull(second.id)
        assertTrue(second.id.isNotEmpty())

        // Each log gets its own generated id.
        assertFalse(first.id == second.id)

        // The three context lookups are hoisted above the map, so each runs once
        // for the whole batch regardless of size (VersionUtils.getVersionName is
        // a PackageManager IPC — the batch path flushes pending logs at startup).
        verify(exactly = 1) { VersionUtils.getVersionName(any()) }
        verify(exactly = 1) { sharedPrefManager.getParentCode() }
        verify(exactly = 1) { sharedPrefManager.getPlanetCode() }
    }

    @Test
    fun `saveLogsToRoom leaves userId null when no user is logged in`() = runTest {
        coEvery { userSessionManager.getUserModel() } returns null

        val inserted = slot<List<ApkLog>>()
        coEvery { apkLogDao.insertAll(capture(inserted)) } returns Unit

        val pending = listOf(
            CrashLogStore.PendingLog(File("/tmp/a.log"), "crash", "1700000000001", "err1")
        )

        repository.saveLogsToRoom(pending)

        assertEquals(1, inserted.captured.size)
        assertNull(inserted.captured[0].userId)
    }

    @Test
    fun `saveLogsToRoom returns false on exception`() = runTest {
        coEvery { userSessionManager.getUserModel() } throws RuntimeException("db down")

        val result = repository.saveLogsToRoom(
            listOf(CrashLogStore.PendingLog(File("/tmp/a.log"), "crash", "1700000000001", "err1"))
        )

        assertFalse(result)
        coVerify(exactly = 0) { apkLogDao.insertAll(any()) }
    }
}
