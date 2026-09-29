package org.ole.planet.myplanet.model

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ole.planet.myplanet.utils.TimeUtils

class UserSurveyProfileTest {

    @Test
    fun `toProfileFieldsUpdate and toJson agree field-for-field for a fully-populated profile`() {
        val dob = "1990-01-15"
        val yob = "1990"
        val profile = UserSurveyProfile(
            fname = "Jane",
            lname = "Doe",
            mName = "M",
            email = "jane@example.com",
            phone = "1234567890",
            dob = dob,
            yob = yob,
            level = "Beginner",
            gender = "Female",
            language = "English"
        )

        val update = profile.toProfileFieldsUpdate()
        val json = profile.toJson()

        val expectedBirthDate = TimeUtils.convertToISO8601(dob)
        val expectedAge = (Calendar.getInstance().get(Calendar.YEAR) - 1990).toString()

        assertEquals("Jane", update.firstName)
        assertEquals(json.get("firstName")?.asString, update.firstName)

        assertEquals("Doe", update.lastName)
        assertEquals(json.get("lastName")?.asString, update.lastName)

        assertEquals("M", update.middleName)
        assertEquals(json.get("middleName")?.asString, update.middleName)

        assertEquals("jane@example.com", update.email)
        assertEquals(json.get("email")?.asString, update.email)

        assertEquals("1234567890", update.phoneNumber)
        assertEquals(json.get("phoneNumber")?.asString, update.phoneNumber)

        assertEquals(expectedBirthDate, update.birthDate)
        assertEquals(json.get("birthDate")?.asString, update.birthDate)

        assertEquals(expectedAge, update.age)
        assertEquals(json.get("age")?.asString, update.age)

        assertEquals("Beginner", update.level)
        assertEquals(json.get("level")?.asString, update.level)

        assertEquals("Female", update.gender)
        assertEquals(json.get("gender")?.asString, update.gender)

        assertEquals("English", update.language)
        assertEquals(json.get("language")?.asString, update.language)
    }

    @Test
    fun `toProfileFieldsUpdate maps empty dob and yob to null`() {
        val profile = UserSurveyProfile(
            fname = "Jane",
            lname = "Doe",
            mName = "",
            email = "",
            phone = "",
            dob = "",
            yob = "",
            level = "",
            gender = "",
            language = ""
        )

        val update = profile.toProfileFieldsUpdate()

        assertNull(update.birthDate)
        assertNull(update.age)
        assertNull(update.middleName)
        assertNull(update.email)
        assertNull(update.phoneNumber)
        assertNull(update.level)
        assertNull(update.gender)
        assertNull(update.language)
    }
}
