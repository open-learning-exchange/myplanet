package org.ole.planet.myplanet.model

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
    fun toJson(): com.google.gson.JsonObject {
        val user = com.google.gson.JsonObject()

        if (fname.isNotEmpty()) user.addProperty("firstName", fname)
        if (mName.isNotEmpty()) user.addProperty("middleName", mName)
        if (lname.isNotEmpty()) user.addProperty("lastName", lname)

        if (email.isNotEmpty()) user.addProperty("email", email)
        if (language.isNotEmpty()) user.addProperty("language", language)

        if (phone.isNotEmpty()) user.addProperty("phoneNumber", phone)

        if (dob.isNotEmpty()) {
            val birthDateISO = org.ole.planet.myplanet.utils.TimeUtils.convertToISO8601(dob)
            user.addProperty("birthDate", birthDateISO)
        }

        if (yob.isNotEmpty()) {
            val yobInt = yob.toIntOrNull()
            if (yobInt != null) {
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val calculatedAge = currentYear - yobInt
                user.addProperty("age", calculatedAge.toString())
            }
        }

        if (level.isNotEmpty()) user.addProperty("level", level)
        if (gender.isNotEmpty()) user.addProperty("gender", gender)

        user.addProperty("betaEnabled", false)

        return user
    }
}