package org.ole.planet.myplanet.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.CrashLogStore
import org.ole.planet.myplanet.utils.VersionUtils

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsRepositoryImplTest {

    private val context: Context = mockk()
    private val apkLogDao: ApkLogDao = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk()
    private lateinit var repository: DiagnosticsRepositoryImpl

    @Before
    fun setUp() {
        mockkObject(VersionUtils)
        every { VersionUtils.getVersionName(context) } returns "0.63.42"
        repository = DiagnosticsRepositoryImpl(context, apkLogDao, userRepository)
    }

    @After
    fun tearDown() {
        unmockkObject(VersionUtils)
    }

    @Test
    fun `saveLogToRoom builds ApkLog with identity fields from UserRepository`() = runTest {
        val user = UserEntity(
            id = "user-1",
            planetCode = "planet-x",
            parentCode = "parent-y"
        )
        coEvery { userRepository.getUserModel() } returns user

        val result = repository.saveLogToRoom("crash", "boom", "12:00")

        assertTrue(result)
        val logSlot = slot<ApkLog>()
        coVerify(exactly = 1) { apkLogDao.insert(capture(logSlot)) }
        val log = logSlot.captured
        assertEquals("user-1", log.userId)
        assertEquals("parent-y", log.parentCode)
        assertEquals("planet-x", log.createdOn)
        assertEquals("0.63.42", log.version)
        assertEquals("crash", log.type)
        assertEquals("boom", log.error)
    }

    @Test
    fun `saveLogToRoom with null user leaves identity fields null and still succeeds`() = runTest {
        coEvery { userRepository.getUserModel() } returns null

        val result = repository.saveLogToRoom("sync", "", "12:01")

        assertTrue(result)
        val logSlot = slot<ApkLog>()
        coVerify(exactly = 1) { apkLogDao.insert(capture(logSlot)) }
        val log = logSlot.captured
        assertEquals(null, log.userId)
        assertEquals(null, log.parentCode)
        assertEquals(null, log.createdOn)
    }

    @Test
    fun `saveLogsToRoom stamps each pending log with the user identity from UserRepository`() = runTest {
        val user = UserEntity(
            id = "user-2",
            planetCode = "planet-a",
            parentCode = "parent-b"
        )
        coEvery { userRepository.getUserModel() } returns user

        val pendingLogs = listOf(
            CrashLogStore.PendingLog(file = java.io.File("/tmp/a"), type = "crash", time = "t1", error = "e1"),
            CrashLogStore.PendingLog(file = java.io.File("/tmp/b"), type = "anr", time = "t2", error = "")
        )

        val result = repository.saveLogsToRoom(pendingLogs)

        assertTrue(result)
        val logsSlot = slot<List<ApkLog>>()
        coVerify(exactly = 1) { apkLogDao.insertAll(capture(logsSlot)) }
        val logs = logsSlot.captured
        assertEquals(2, logs.size)
        logs.forEach { log ->
            assertEquals("user-2", log.userId)
            assertEquals("parent-b", log.parentCode)
            assertEquals("planet-a", log.createdOn)
            assertEquals("0.63.42", log.version)
        }
        assertEquals("t1", logs[0].time)
        assertEquals("e1", logs[0].error)
        assertEquals("t2", logs[1].time)
    }

    @Test
    fun `saveLogsToRoom returns true without inserting when pending list is empty`() = runTest {
        val result = repository.saveLogsToRoom(emptyList())

        assertTrue(result)
        coVerify(exactly = 0) { apkLogDao.insertAll(any()) }
    }
}
