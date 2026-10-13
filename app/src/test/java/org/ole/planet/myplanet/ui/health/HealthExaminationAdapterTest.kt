package org.ole.planet.myplanet.ui.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HealthExaminationAdapterTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val currentUser = UserEntity(
        id = "user_1",
        key = "000102030405060708090a0b0c0d0e0f000102030405060708090a0b0c0d0e0f",
        iv = "000102030405060708090a0b0c0d0e0f"
    )

    private val mainExamination = HealthExamination().apply { _id = "exam_main" }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createEncryptedDataWithCreatedBy(createdByValue: String): String {
        val encrypted = JsonObject()
        encrypted.addProperty("createdBy", createdByValue)
        return AndroidDecrypter.encrypt(encrypted.toString(), currentUser.key, currentUser.iv)
    }

    @Test
    fun submitExaminations_withColonInCreatedBy_resolvesToSubstringAfterColon() = runTest(testDispatcher) {
        val userMap = emptyMap<String, UserEntity>()
        val adapter = HealthExaminationAdapter(context, mainExamination, currentUser, userMap, dispatcherProvider)

        val examData = createEncryptedDataWithCreatedBy("org.couchdb.user:alice")
        val exam = HealthExamination().apply {
            _id = "exam_1"
            date = 1000L
            data = examData
        }

        adapter.submitExaminations(listOf(exam))

        val currentList = adapter.currentList
        assertEquals(1, currentList.size)
        val item = currentList[0]
        assertFalse(item.isSelfExamination)
        assertEquals("alice", item.resolvedName)
    }

    @Test
    fun submitExaminations_withoutColonInCreatedBy_resolvesToWholeString() = runTest(testDispatcher) {
        val userMap = emptyMap<String, UserEntity>()
        val adapter = HealthExaminationAdapter(context, mainExamination, currentUser, userMap, dispatcherProvider)

        val examData = createEncryptedDataWithCreatedBy("alice")
        val exam = HealthExamination().apply {
            _id = "exam_2"
            date = 1000L
            data = examData
        }

        adapter.submitExaminations(listOf(exam))

        val currentList = adapter.currentList
        assertEquals(1, currentList.size)
        val item = currentList[0]
        assertFalse(item.isSelfExamination)
        assertEquals("alice", item.resolvedName)
    }

    @Test
    fun submitExaminations_withTrailingColonInCreatedBy_resolvesToWholeStringNotEmpty() = runTest(testDispatcher) {
        val userMap = emptyMap<String, UserEntity>()
        val adapter = HealthExaminationAdapter(context, mainExamination, currentUser, userMap, dispatcherProvider)

        val examData = createEncryptedDataWithCreatedBy("org.couchdb.user:")
        val exam = HealthExamination().apply {
            _id = "exam_3"
            date = 1000L
            data = examData
        }

        adapter.submitExaminations(listOf(exam))

        val currentList = adapter.currentList
        assertEquals(1, currentList.size)
        val item = currentList[0]
        assertFalse(item.isSelfExamination)
        assertEquals("org.couchdb.user:", item.resolvedName)
    }

    @Test
    fun submitExaminations_userInMap_prefersModelFullNameFirst() = runTest(testDispatcher) {
        val mappedUser = UserEntity(
            id = "org.couchdb.user:bob",
            firstName = "Bob",
            lastName = "Smith"
        )
        val userMap = mapOf("org.couchdb.user:bob" to mappedUser)
        val adapter = HealthExaminationAdapter(context, mainExamination, currentUser, userMap, dispatcherProvider)

        val examData = createEncryptedDataWithCreatedBy("org.couchdb.user:bob")
        val exam = HealthExamination().apply {
            _id = "exam_4"
            date = 1000L
            data = examData
        }

        adapter.submitExaminations(listOf(exam))

        val currentList = adapter.currentList
        assertEquals(1, currentList.size)
        val item = currentList[0]
        assertFalse(item.isSelfExamination)
        assertEquals("Bob Smith", item.resolvedName)
    }
}
