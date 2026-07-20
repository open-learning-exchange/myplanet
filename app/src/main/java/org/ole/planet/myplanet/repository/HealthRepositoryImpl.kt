package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.HealthExaminationDao
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmHealthExamination.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

class HealthRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
    private val dispatcherProvider: DispatcherProvider,
    private val healthExaminationDao: HealthExaminationDao
) : RealmRepository(databaseService, realmDispatcher), HealthRepository {
    override suspend fun getHealthEntry(userId: String): Pair<RealmUser?, RealmHealthExamination?> {
        val userCopy = findByField(RealmUser::class.java, "_id", userId)
            ?: findByField(RealmUser::class.java, "id", userId)
        val pojoCopy = healthExaminationDao.getByIdOrUserId(userId)

        return Pair(userCopy, pojoCopy)
    }

    override suspend fun getExaminationById(id: String): RealmHealthExamination? {
        return healthExaminationDao.getById(id)
    }

    override suspend fun initHealth(): RealmMyHealth {
        return withContext(dispatcherProvider.default) {
            val health = RealmMyHealth()
            val profile = RealmMyHealth.RealmMyHealthProfile()
            health.lastExamination = Date().time
            health.userKey = AndroidDecrypter.generateKey()
            health.profile = profile
            health
        }
    }

    override suspend fun getUpdatedHealthExaminations(): List<RealmHealthExamination> {
        return healthExaminationDao.getUpdated()
    }

    override suspend fun getUpdatedHealthForUser(userId: String): List<RealmHealthExamination> {
        return healthExaminationDao.getUpdatedForUser(userId)
    }

    override suspend fun markHealthExaminationsUploaded(idToRevMap: Map<String, String?>) {
        idToRevMap.forEach { (id, rev) ->
            healthExaminationDao.markUploaded(id, rev)
        }
    }

    override suspend fun saveExamination(examination: RealmHealthExamination?, pojo: RealmHealthExamination?, user: RealmUser?) {
        user?.let { save(it) }
        pojo?.let { healthExaminationDao.upsert(it) }
        examination?.let { healthExaminationDao.upsert(it) }
    }

    override suspend fun updateExaminationUserId(id: String, userId: String) {
        healthExaminationDao.updateUserId(id, userId)
    }

    override suspend fun bulkInsertFromSync(jsonArray: JsonArray) {
        val examinations = ArrayList<RealmHealthExamination>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                examinations.add(RealmHealthExamination.fromJson(jsonDoc))
            }
        }
        healthExaminationDao.upsertAll(examinations)
    }

    override suspend fun getExaminationConditions(examination: RealmHealthExamination?): Map<String, Boolean> {
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

    override suspend fun uploadHealthData(myHealths: List<RealmHealthExamination>): Map<String, String?> {
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

}
