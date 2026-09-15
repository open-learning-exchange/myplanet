package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import java.util.Calendar
import org.ole.planet.myplanet.repository.ProfileFieldsUpdate
import org.ole.planet.myplanet.utils.TimeUtils

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
    fun toJson(): JsonObject {
        val user = JsonObject()

        if (fname.isNotEmpty()) user.addProperty("firstName", fname)
        if (mName.isNotEmpty()) user.addProperty("middleName", mName)
        if (lname.isNotEmpty()) user.addProperty("lastName", lname)

        if (email.isNotEmpty()) user.addProperty("email", email)
        if (language.isNotEmpty()) user.addProperty("language", language)

        if (phone.isNotEmpty()) user.addProperty("phoneNumber", phone)

        if (dob.isNotEmpty()) {
            val birthDateISO = TimeUtils.convertToISO8601(dob)
            user.addProperty("birthDate", birthDateISO)
        }

        if (yob.isNotEmpty()) {
            val yobInt = yob.toInt()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val calculatedAge = currentYear - yobInt
            user.addProperty("age", calculatedAge.toString())
        }

        if (level.isNotEmpty()) user.addProperty("level", level)
        if (gender.isNotEmpty()) user.addProperty("gender", gender)

        user.addProperty("betaEnabled", false)

        return user
    }

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
