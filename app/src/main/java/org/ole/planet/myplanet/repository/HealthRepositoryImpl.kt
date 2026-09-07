package org.ole.planet.myplanet.repository

import android.text.TextUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.HealthExaminationDao
import org.ole.planet.myplanet.di.PlainGson
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.HealthExamination.Companion.serialize
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.toSyncDocuments

class HealthRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider,
    private val healthExaminationDao: HealthExaminationDao,
    private val userRepository: Lazy<UserRepository>,
    @PlainGson private val gson: Gson
) : HealthRepository {
    override suspend fun getHealthEntry(userId: String): Pair<UserEntity?, HealthExamination?> {
        val userCopy = userRepository.get().getUserById(userId)
        val pojoCopy = healthExaminationDao.getByIdOrUserId(userId)

        return Pair(userCopy, pojoCopy)
    }

    override suspend fun getExaminationById(id: String): HealthExamination? {
        return healthExaminationDao.getById(id)
    }

    override suspend fun initHealth(): MyHealth {
        return withContext(dispatcherProvider.default) {
            val health = MyHealth()
            val profile = MyHealth.MyHealthProfile()
            health.lastExamination = Date().time
            health.userKey = AndroidDecrypter.generateKey()
            health.profile = profile
            health
        }
    }

    override suspend fun getUpdatedHealthExaminations(): List<HealthExamination> {
        return healthExaminationDao.getUpdated()
    }

    override suspend fun getUpdatedHealthForUser(userId: String): List<HealthExamination> {
        return healthExaminationDao.getUpdatedForUser(userId)
    }

    override suspend fun markHealthExaminationsUploaded(idToRevMap: Map<String, String?>) {
        healthExaminationDao.markUploaded(idToRevMap)
    }

    override suspend fun saveExamination(examination: HealthExamination?, pojo: HealthExamination?, user: UserEntity?) {
        user?.let { userRepository.get().saveUser(it) }
        pojo?.let { healthExaminationDao.upsert(it) }
        examination?.let { healthExaminationDao.upsert(it) }
    }

    override suspend fun updateExaminationUserId(id: String, userId: String) {
        healthExaminationDao.updateUserId(id, userId)
    }

    override suspend fun bulkInsertFromSync(jsonArray: JsonArray) {
        val examinations = jsonArray.toSyncDocuments().map { (_, doc) -> HealthExamination.fromJson(doc) }
        healthExaminationDao.upsertAll(examinations)
    }

    override suspend fun getExaminationConditions(examination: HealthExamination?): Map<String, Boolean> {
        return withContext(dispatcherProvider.default) {
            val result = mutableMapOf<String, Boolean>()
            if (examination != null && !examination.conditions.isNullOrEmpty()) {
                try {
                    val conditions = gson.fromJson(examination.conditions, JsonObject::class.java)
                    for (key in conditions.keySet()) {
                        result[key] = JsonUtils.getBoolean(key, conditions)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            result
        }
    }

    override suspend fun uploadHealthData(myHealths: List<HealthExamination>): Map<String, String?> {
        val uploadedHealths = mutableMapOf<String, String?>()
        val semaphore = Semaphore(5)
        supervisorScope {
            myHealths.map { pojo ->
                async {
                    semaphore.withPermit {
                        try {
                            val res = apiInterface.postDoc(
                                UrlUtils.header,
                                "application/json",
                                "${UrlUtils.getUrl()}/health",
                                serialize(pojo)
                            )

                            if (res.body() != null && res.body()?.has("id") == true) {
                                val rev = res.body()?.get("rev")?.asString
                                return@async pojo._id to rev
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                        null
                    }
                }
            }.awaitAll().filterNotNull().forEach { (id, rev) ->
                uploadedHealths[id] = rev
            }
        }
        return uploadedHealths
    }

    override suspend fun getByIdOrUserId(id: String): HealthExamination? {
        return healthExaminationDao.getByIdOrUserId(id)
    }

    override suspend fun getByProfileId(profileId: String): List<HealthExamination> {
        return healthExaminationDao.getByProfileId(profileId)
    }

    override suspend fun upsert(examination: HealthExamination) {
        healthExaminationDao.upsert(examination)
    }

    override suspend fun getPatientById(id: String): UserEntity? {
        return userRepository.get().getUserById(id)
    }

    override suspend fun getPatientsSortedBy(fieldName: String, descending: Boolean): List<UserEntity> {
        return userRepository.get().getUsersSortedBy(fieldName, descending)
    }

    override suspend fun searchPatients(query: String, sortField: String, descending: Boolean): List<UserEntity> {
        return if (query.isBlank()) {
            userRepository.get().getUsersSortedBy(sortField, descending)
        } else {
            userRepository.get().searchUsers(query, sortField, descending)
        }
    }

    private fun decodeHealth(healthPojo: HealthExamination?, userModel: UserEntity?): MyHealth? {
        val data = healthPojo?.data
        if (data.isNullOrEmpty()) return null
        return try {
            val decrypted = AndroidDecrypter.decrypt(data, userModel?.key, userModel?.iv)
            gson.fromJson(decrypted, MyHealth::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getHealthProfile(userId: String): MyHealth? {
        val userModel = userRepository.get().getUserById(userId)
        return decodeHealth(healthExaminationDao.getByIdOrUserId(userId), userModel)
    }

    override suspend fun getDecryptedHealth(pojo: HealthExamination?, user: UserEntity?): MyHealth? {
        return decodeHealth(pojo, user)
    }

    private fun applyUserDetails(userModel: UserEntity, userData: Map<String, Any?>) {
        userModel.apply {
            firstName = (userData["firstName"] as? String)?.trim()
            middleName = (userData["middleName"] as? String)?.trim()
            lastName = (userData["lastName"] as? String)?.trim()
            email = (userData["email"] as? String)?.trim()
            phoneNumber = (userData["phoneNumber"] as? String)?.trim()
            birthPlace = (userData["birthPlace"] as? String)?.trim()
            userData["dob"]?.let { dobVal ->
                val dobInput = (dobVal as String).trim()
                dob = TimeUtils.convertDDMMYYYYToISO(dobInput)
            }
            isUpdated = true
        }
    }

    private fun applyProfileFields(profile: MyHealth.MyHealthProfile, userData: Map<String, Any?>) {
        fun trimmed(key: String) = (userData[key] as? String)?.trim() ?: ""

        profile.emergencyContactName = trimmed("emergencyContactName")
        profile.emergencyContact = trimmed("emergencyContact").ifEmpty { profile.emergencyContact }
        profile.emergencyContactType = trimmed("emergencyContactType").ifEmpty { profile.emergencyContactType }
        profile.specialNeeds = trimmed("specialNeeds")
        profile.notes = trimmed("notes")
    }

    override suspend fun updateUserHealthProfile(userId: String, userData: Map<String, Any?>) {
        val userModel = userRepository.get().getUserById(userId)
        val healthPojo = healthExaminationDao.getByIdOrUserId(userId) ?: HealthExamination().apply { _id = userId }

        userModel?.let {
            applyUserDetails(it, userData)
            userRepository.get().saveUser(it)
        }

        val myHealth = decodeHealth(healthPojo, userModel) ?: MyHealth()
        if (TextUtils.isEmpty(myHealth.userKey)) {
            myHealth.userKey = AndroidDecrypter.generateKey()
        }

        val profile = myHealth.profile ?: MyHealth.MyHealthProfile().also { myHealth.profile = it }
        applyProfileFields(profile, userData)

        healthPojo.userId = userModel?._id
        healthPojo.isUpdated = true

        try {
            val key = userModel?.key ?: AndroidDecrypter.generateKey().also { newKey -> userModel?.key = newKey }
            val iv = userModel?.iv ?: AndroidDecrypter.generateIv().also { newIv -> userModel?.iv = newIv }
            healthPojo.data = AndroidDecrypter.encrypt(gson.toJson(myHealth), key, iv)
            userModel?.let { userRepository.get().saveUser(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        healthExaminationDao.upsert(healthPojo)
    }

    override suspend fun getPatientHealthRecords(userId: String, currentUser: UserEntity): HealthRecord? {
        val mh = getByIdOrUserId(userId) ?: return null
        val json = AndroidDecrypter.decrypt(mh.data, currentUser.key, currentUser.iv)
        val mm = if (json.isNullOrEmpty()) {
            null
        } else {
            try {
                gson.fromJson(json, MyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } ?: return null

        val list = getByProfileId(mm.userKey ?: "")
        if (list.isEmpty()) {
            return HealthRecord(mh, mm, emptyList(), emptyMap())
        }

        val userIds = list.mapNotNull {
            it.getEncryptedDataAsJson(currentUser).let { jsonData ->
                jsonData.get("createdBy")?.asString
            }
        }.distinct()

        val userMap = if (userIds.isEmpty()) {
            emptyMap()
        } else {
            userRepository.get().getUsersByIds(userIds)
                .associateBy { it.id ?: "" }
        }
        return HealthRecord(mh, mm, list, userMap)
    }
}
