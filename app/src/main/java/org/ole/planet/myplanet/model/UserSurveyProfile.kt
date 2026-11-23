package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import java.util.Calendar
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.repository.ProfileFieldsUpdate
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.toGson

data class UserSurveyProfile(
    val fname: String,
    val lname: String,
    val mName: String,
    val email: String,
    val phone: String,
    val dob: String,
    val yob: String,
    val level: String,
    val gender: String,
    val language: String
) {
    fun toJson(): JsonObject = buildJsonObject {
        if (fname.isNotEmpty()) put("firstName", fname)
        if (mName.isNotEmpty()) put("middleName", mName)
        if (lname.isNotEmpty()) put("lastName", lname)

        if (email.isNotEmpty()) put("email", email)
        if (language.isNotEmpty()) put("language", language)

        if (phone.isNotEmpty()) put("phoneNumber", phone)

        if (dob.isNotEmpty()) {
            val birthDateISO = TimeUtils.convertToISO8601(dob)
            put("birthDate", birthDateISO)
        }

        if (yob.isNotEmpty()) {
            val yobInt = yob.toInt()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val calculatedAge = currentYear - yobInt
            put("age", calculatedAge.toString())
        }

        if (level.isNotEmpty()) put("level", level)
        if (gender.isNotEmpty()) put("gender", gender)

        put("betaEnabled", false)
    }.toGson()

    fun toProfileFieldsUpdate(): ProfileFieldsUpdate {
        val birthDateCalculated = if (dob.isNotEmpty()) {
            TimeUtils.convertToISO8601(dob)
        } else {
            null
        }

        val ageCalculated = if (yob.isNotEmpty()) {
            val yobInt = yob.toInt()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            (currentYear - yobInt).toString()
        } else {
            null
        }

        return ProfileFieldsUpdate(
            firstName = fname.takeIf { it.isNotEmpty() },
            lastName = lname.takeIf { it.isNotEmpty() },
            middleName = mName.takeIf { it.isNotEmpty() },
            email = email.takeIf { it.isNotEmpty() },
            language = language.takeIf { it.isNotEmpty() },
            phoneNumber = phone.takeIf { it.isNotEmpty() },
            birthDate = birthDateCalculated,
            level = level.takeIf { it.isNotEmpty() },
            gender = gender.takeIf { it.isNotEmpty() },
            age = ageCalculated
        )
    }
}
