package org.ole.planet.myplanet.ui.health

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.TimeUtils

@RunWith(AndroidJUnit4::class)
class HealthExaminationItemMapperTest {

    private val currentUser = UserEntity(
        id = "user_1",
        key = "000102030405060708090a0b0c0d0e0f000102030405060708090a0b0c0d0e0f",
        iv = "000102030405060708090a0b0c0d0e0f"
    )

    private fun createEncryptedDataWithCreatedBy(createdByValue: String?): String {
        val encrypted = JsonObject()
        createdByValue?.let { encrypted.addProperty("createdBy", it) }
        return AndroidDecrypter.encrypt(encrypted.toString(), currentUser.key, currentUser.iv)
    }

    @Test
    fun map_createdByEqualsUserModelId_isSelfExaminationAndEmptyResolvedName() {
        val examData = createEncryptedDataWithCreatedBy(currentUser.id)
        val exam = HealthExamination().apply {
            _id = "exam_1"
            date = 1000L
            data = examData
        }

        val result = HealthExaminationItemMapper.map(listOf(exam), currentUser, emptyMap())

        assertEquals(1, result.size)
        val item = result[0]
        assertTrue(item.isSelfExamination)
        assertEquals("", item.resolvedName)
    }

    @Test
    fun map_emptyOrMissingCreatedBy_countsAsSelfExamination() {
        val examDataEmpty = createEncryptedDataWithCreatedBy("")
        val examDataMissing = createEncryptedDataWithCreatedBy(null)

        val exam1 = HealthExamination().apply {
            _id = "exam_empty"
            date = 1000L
            data = examDataEmpty
        }
        val exam2 = HealthExamination().apply {
            _id = "exam_missing"
            date = 2000L
            data = examDataMissing
        }

        val result = HealthExaminationItemMapper.map(listOf(exam1, exam2), currentUser, emptyMap())

        assertEquals(2, result.size)
        assertTrue(result[0].isSelfExamination)
        assertEquals("", result[0].resolvedName)

        assertTrue(result[1].isSelfExamination)
        assertEquals("", result[1].resolvedName)
    }

    @Test
    fun map_userModelNull_givesNullEncryptedAndSelfExamination() {
        val exam = HealthExamination().apply {
            _id = "exam_no_user"
            date = 1000L
            data = "some_encrypted_data"
        }

        val result = HealthExaminationItemMapper.map(listOf(exam), null, emptyMap())

        assertEquals(1, result.size)
        val item = result[0]
        assertNull(item.encrypted)
        assertTrue(item.isSelfExamination)
        assertEquals("", item.resolvedName)
    }

    @Test
    fun map_twoItemsSameCreatedBy_bothResolveToSameNameAndUserMapWinsOverFallback() {
        val mappedUser = UserEntity(
            id = "org.couchdb.user:doctor",
            firstName = "Doctor",
            lastName = "Strange"
        )
        val userMap = mapOf("org.couchdb.user:doctor" to mappedUser)

        val examData = createEncryptedDataWithCreatedBy("org.couchdb.user:doctor")
        val exam1 = HealthExamination().apply {
            _id = "exam_doc_1"
            date = 1000L
            data = examData
        }
        val exam2 = HealthExamination().apply {
            _id = "exam_doc_2"
            date = 2000L
            data = examData
        }

        val result = HealthExaminationItemMapper.map(listOf(exam1, exam2), currentUser, userMap)

        assertEquals(2, result.size)
        assertEquals("Doctor Strange", result[0].resolvedName)
        assertEquals("Doctor Strange", result[1].resolvedName)
    }

    @Test
    fun map_formattedDate_equalsTimeUtilsFormatDate() {
        val testDate = 1600000000000L
        val exam = HealthExamination().apply {
            _id = "exam_date"
            date = testDate
        }

        val result = HealthExaminationItemMapper.map(listOf(exam), currentUser, emptyMap())

        assertEquals(1, result.size)
        val expectedFormattedDate = TimeUtils.formatDate(testDate, "MMM dd, yyyy")
        assertEquals(expectedFormattedDate, result[0].formattedDate)
    }
}
