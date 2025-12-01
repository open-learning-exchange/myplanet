package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.myhealth.HealthExaminationItem
import org.ole.planet.myplanet.ui.myhealth.MyHealthProfile
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities

class HealthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context, override var databaseService: DatabaseService
) : HealthRepository {

    private val colonRegex by lazy { ":".toRegex() }

    override suspend fun getMyHealthProfile(userId: String, user: RealmUserModel): MyHealthProfile? {
        return withRealm { realm ->
            var healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
            if (healthPojo == null) {
                healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
            }

            if (healthPojo != null) {
                val healthProfile = getHealthProfile(healthPojo, user)
                if (healthProfile != null) {
                    val examinations = getExaminations(realm, healthProfile, user, userId)
                    return@withRealm MyHealthProfile(
                        fullName = context.getString(
                            R.string.three_strings,
                            user.firstName,
                            user.middleName,
                            user.lastName
                        ),
                        email = Utilities.checkNA(user.email),
                        language = Utilities.checkNA(user.language),
                        dob = Utilities.checkNA(user.dob),
                        birthPlace = Utilities.checkNA(user.birthPlace),
                        emergencyContact = getEmergencyContactDetails(healthProfile),
                        specialNeeds = Utilities.checkNA(healthProfile.profile?.specialNeeds),
                        otherNeeds = Utilities.checkNA(healthProfile.profile?.notes),
                        examinations = examinations,
                        showPatientCard = true,
                    )
                }
            }
            null
        }
    }

    private fun getHealthProfile(mh: RealmMyHealthPojo, userModel: RealmUserModel): RealmMyHealth? {
        val json = AndroidDecrypter.decrypt(mh.data, userModel.key, userModel.iv)
        return if (json.isNullOrEmpty()) {
            null
        } else {
            try {
                GsonUtils.gson.fromJson(json, RealmMyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun getExaminations(
        realm: Realm,
        healthProfile: RealmMyHealth,
        user: RealmUserModel,
        userId: String
    ): List<HealthExaminationItem> {
        val healths = realm.where(RealmMyHealthPojo::class.java)
            .equalTo("profileId", healthProfile.userKey)
            .findAll()

        return healths?.mapNotNull { item ->
            val encrypted = item.getEncryptedDataAsJson(user)
            val createdBy = JsonUtils.getString("createdBy", encrypted)
            val name = if (!createdBy.isNullOrEmpty() && createdBy != user.id) {
                val model = realm.where(RealmUserModel::class.java).equalTo("id", createdBy).findFirst()
                model?.getFullName() ?: createdBy.split(colonRegex).lastOrNull() ?: createdBy
            } else {
                null
            }
            val formattedDate = TimeUtils.formatDate(item.date, "MMM dd, yyyy")
            HealthExaminationItem(
                _id = item._id ?: "",
                userId = userId,
                temperature = checkEmpty(item.temperature),
                pulse = checkEmptyInt(item.pulse),
                bloodPressure = item.bp ?: "",
                height = checkEmpty(item.height),
                weight = checkEmpty(item.weight),
                vision = item.vision ?: "",
                hearing = item.hearing ?: "",
                date = item.date,
                dateText = formattedDate,
                displayDate = if (name != null) context.getString(
                    R.string.two_strings,
                    formattedDate,
                    name
                ) else context.getString(R.string.self_examination, formattedDate),
                createdBy = name ?: "",
                isSelfExamination = name == null,
                vitals = getVitalsText(item),
                conditions = getConditionsText(item),
                otherNotes = getOtherNotesText(encrypted)
            )
        } ?: emptyList()
    }

    private fun getEmergencyContactDetails(healthProfile: RealmMyHealth): String {
        return context.getString(
            R.string.emergency_contact_details,
            Utilities.checkNA(healthProfile.profile?.emergencyContactName),
            Utilities.checkNA(healthProfile.profile?.emergencyContactType),
            Utilities.checkNA(healthProfile.profile?.emergencyContact)
        ).trimIndent()
    }

    private fun checkEmpty(value: Float): String {
        return if (value == 0f) "" else value.toString()
    }

    private fun checkEmptyInt(value: Int): String {
        return if (value == 0) "" else value.toString()
    }

    private fun getVitalsText(item: RealmMyHealthPojo): String {
        return context.getString(
            R.string.vitals_format,
            checkEmpty(item.temperature),
            checkEmptyInt(item.pulse),
            item.bp,
            checkEmpty(item.height),
            checkEmpty(item.weight),
            item.vision,
            item.hearing
        ).trimIndent()
    }

    private fun getConditionsText(item: RealmMyHealthPojo): String {
        val conditionsMap = GsonUtils.gson.fromJson(item.conditions, JsonObject::class.java)
        return conditionsMap.keySet().filter { conditionsMap[it].asBoolean }.joinToString(", ")
    }

    private fun getOtherNotesText(encrypted: JsonObject): String {
        return context.getString(
            R.string.observations_notes_colon,
            Utilities.checkNA(JsonUtils.getString("notes", encrypted)),
            Utilities.checkNA(JsonUtils.getString("diagnosis", encrypted)),
            Utilities.checkNA(JsonUtils.getString("treatments", encrypted)),
            Utilities.checkNA(JsonUtils.getString("medications", encrypted)),
            Utilities.checkNA(JsonUtils.getString("immunizations", encrypted)),
            Utilities.checkNA(JsonUtils.getString("allergies", encrypted)),
            Utilities.checkNA(JsonUtils.getString("xrays", encrypted)),
            Utilities.checkNA(JsonUtils.getString("tests", encrypted)),
            Utilities.checkNA(JsonUtils.getString("referrals", encrypted))
        )
    }
}
