package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.HealthExamination.Companion.serialize
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

class HealthRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider,
    private val healthExaminationDao: HealthExaminationDao,
    private val userRepositoryLazy: dagger.Lazy<UserRepository>
) : HealthRepository {
    override suspend fun getHealthEntry(userId: String): Pair<UserEntity?, HealthExamination?> {
        val userCopy = userRepositoryLazy.get().getUserById(userId)
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
        user?.let { userRepositoryLazy.get().saveUser(it) }
        pojo?.let { healthExaminationDao.upsert(it) }
        examination?.let { healthExaminationDao.upsert(it) }
    }

    override suspend fun updateExaminationUserId(id: String, userId: String) {
        healthExaminationDao.updateUserId(id, userId)
    }

    override suspend fun bulkInsertFromSync(jsonArray: JsonArray) {
        val examinations = ArrayList<HealthExamination>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                examinations.add(HealthExamination.fromJson(jsonDoc))
            }
        }
        healthExaminationDao.upsertAll(examinations)
    }

    override suspend fun getExaminationConditions(examination: HealthExamination?): Map<String, Boolean> {
        return withContext(dispatcherProvider.default) {
            val result = mutableMapOf<String, Boolean>()
            if (examination != null && !examination.conditions.isNullOrEmpty()) {
                try {
                    val conditions = JsonUtils.gson.fromJson(examination.conditions, JsonObject::class.java)
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
        return userRepositoryLazy.get().getUserById(id)
    }

    override suspend fun getPatientsSortedBy(fieldName: String, descending: Boolean): List<UserEntity> {
        return userRepositoryLazy.get().getUsersSortedBy(fieldName, descending)
    }

    override suspend fun searchPatients(query: String, sortField: String, descending: Boolean): List<UserEntity> {
        val users = if (query.isBlank()) {
            userRepositoryLazy.get().getUsersSortedBy(sortField, descending)
        } else {
            val searchedUsers = userRepositoryLazy.get().searchUsers(query)
            sortPatients(searchedUsers, sortField, descending)
        }
        return users
    }

    private fun sortPatients(users: List<UserEntity>, fieldName: String, descending: Boolean): List<UserEntity> {
        fun value(value: String?) = value.orEmpty().lowercase()
        return when (fieldName) {
            "joinDate" -> if (descending) users.sortedByDescending { it.joinDate } else users.sortedBy { it.joinDate }
            "name" -> if (descending) users.sortedByDescending { value(it.name) } else users.sortedBy { value(it.name) }
            else -> users
        }
    }

    override suspend fun getPatientHealthRecords(userId: String, currentUser: UserEntity): HealthRecord? {
        val mh = getByIdOrUserId(userId) ?: return null
        val json = AndroidDecrypter.decrypt(mh.data, currentUser.key, currentUser.iv)
        val mm = if (json.isNullOrEmpty()) {
            null
        } else {
            try {
                JsonUtils.gson.fromJson(json, MyHealth::class.java)
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
            userRepositoryLazy.get().getUsersByIds(userIds)
                .associateBy { it.id ?: "" }
        }
        return HealthRecord(mh, mm, list, userMap)
    }
}
