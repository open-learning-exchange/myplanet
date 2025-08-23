package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyTeam

interface NewsRepository {
    suspend fun getAllNews(): List<RealmNews>
    suspend fun getNews(userId: String?, planetCode: String?, parentCode: String?): List<RealmNews>
    suspend fun getNews(id: String?): RealmNews?
    suspend fun getMyLibrary(resourceIds: List<String>): List<RealmMyLibrary>
    suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel, imageList: ArrayList<String>): RealmNews?
    suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel, imageList: RealmList<String>?): RealmNews?
    suspend fun getUser(userId: String?): RealmUserModel?
    suspend fun isTeamLeader(teamId: String, userId: String?): Boolean
    suspend fun getReplies(newsId: String?): List<RealmNews>
    suspend fun getLibrary(resourceId: String): RealmMyLibrary?
    suspend fun deleteNews(news: RealmNews?)
    suspend fun shareNews(news: RealmNews?, userId: String?, planetCode: String?, parentCode: String?, teamName: String?)
    suspend fun addLabelToNews(newsId: String?, label: String)
    suspend fun removeLabelFromNews(newsId: String?, label: String)
    suspend fun updateNews(id: String?, message: String)
    fun getTeam(teamId: String?): RealmMyTeam?
    suspend fun getUsers(teamId: String?): List<RealmUserModel>
    suspend fun createTeamLog(teamId: String?, user: RealmUserModel?)
    suspend fun getNewsByTeam(teamId: String?): List<RealmNews>
    suspend fun getCoursesByTeam(teamId: String?): List<RealmMyCourse>
    suspend fun getResourcesByTeam(teamId: String?): io.realm.RealmResults<RealmMyLibrary>
    suspend fun getVisitCount(name: String?, teamId: String?): Long
    suspend fun createNotification(effectiveTeamId: String, count: Int)
}

class NewsRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
) : RealmRepository(databaseService), NewsRepository {
    private val gson = Gson()

    override suspend fun getAllNews(): List<RealmNews> = withRealm { realm ->
        realm.where(RealmNews::class.java).sort("time", Sort.DESCENDING)
            .isEmpty("replyTo").equalTo("docType", "message", Case.INSENSITIVE)
            .findAll()
    }

    override suspend fun getNews(id: String?): RealmNews? = withRealm { realm ->
        realm.where(RealmNews::class.java).equalTo("id", id).findFirst()
    }

    override suspend fun getNews(userId: String?, planetCode: String?, parentCode: String?): List<RealmNews> = withRealm { realm ->
        val allNews: List<RealmNews> = realm.where(RealmNews::class.java).isEmpty("replyTo")
            .equalTo("docType", "message", Case.INSENSITIVE).findAll()
        val list: MutableList<RealmNews> = ArrayList()
        for (news in allNews) {
            if (news.viewableBy.equals("community", ignoreCase = true)) {
                list.add(news)
                continue
            }
            if (!news.viewIn.isNullOrEmpty()) {
                val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
                for (e in ar) {
                    val ob = e.asJsonObject
                    var fullId = "$planetCode@$parentCode"
                    if (fullId.isEmpty() || fullId == "@") {
                        fullId = userId ?: ""
                    }
                    if (ob != null && ob.has("_id") && ob["_id"].asString.equals(fullId, ignoreCase = true)) {
                        list.add(news)
                    }
                }
            }
        }
        list
    }

    override suspend fun getMyLibrary(resourceIds: List<String>): List<RealmMyLibrary> = withRealm { realm ->
        realm.where(RealmMyLibrary::class.java)
            .`in`("_id", resourceIds.toTypedArray())
            .findAll()
    }

    override suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel, imageList: ArrayList<String>): RealmNews? = withRealm { realm ->
        var news: RealmNews? = null
        realm.executeTransaction {
            val realmImageUrls = io.realm.RealmList<String>()
            realmImageUrls.addAll(imageList)
            news = RealmNews.createNews(map, it, user, realmImageUrls)
        }
        news
    }

    override suspend fun createNews(map: HashMap<String?, String>, user: RealmUserModel, imageList: RealmList<String>?): RealmNews? = withRealm { realm ->
        var news: RealmNews? = null
        realm.executeTransaction {
            news = RealmNews.createNews(map, it, user, imageList, isReply = true)
        }
        news
    }

    override suspend fun getUser(userId: String?): RealmUserModel? = withRealm { realm ->
        realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
    }

    override suspend fun updateNews(id: String?, message: String) = withRealm { realm ->
        realm.executeTransaction {
            val news = it.where(RealmNews::class.java).equalTo("id", id).findFirst()
            news?.message = message
        }
    }

    override fun getTeam(teamId: String?): RealmMyTeam? {
        return databaseService.realmInstance.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
    }

    override suspend fun isTeamLeader(teamId: String, userId: String?): Boolean = withRealm { realm ->
        realm.where(org.ole.planet.myplanet.model.RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findFirst()?.userId == userId
    }

    override suspend fun getUsers(teamId: String?): List<RealmUserModel> = withRealm { realm ->
        val team = realm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
        team?.getUsers(realm, "") ?: emptyList()
    }

    override suspend fun createTeamLog(teamId: String?, user: RealmUserModel?) = withRealm { realm ->
        realm.executeTransaction {
            val log = it.createObject(org.ole.planet.myplanet.model.RealmTeamLog::class.java, "${java.util.UUID.randomUUID()}")
            log.teamId = teamId
            log.user = user?.name
            log.createdOn = user?.planetCode
            log.type = "teamVisit"
            log.teamType = getTeam(teamId)?.teamType
            log.parentCode = user?.parentCode
            log.time = java.util.Date().time
        }
    }

    override suspend fun getNewsByTeam(teamId: String?): List<RealmNews> = withRealm { realm ->
        realm.where(RealmNews::class.java)
            .isEmpty("replyTo")
            .equalTo("viewableBy", "teams")
            .equalTo("viewableId", teamId)
            .findAll()
    }

    override suspend fun getCoursesByTeam(teamId: String?): List<RealmMyCourse> = withRealm { realm ->
        val team = realm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
        realm.where(RealmMyCourse::class.java).`in`("id", team?.courses?.toTypedArray<String>()).findAll()
    }

    override suspend fun getResourcesByTeam(teamId: String?): io.realm.RealmResults<RealmMyLibrary> = withRealm { realm ->
        val team = realm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
        realm.where(RealmMyLibrary::class.java).`in`("id", team?.getResourceIds(realm)?.toTypedArray<String>()).findAll()
    }

    override suspend fun getVisitCount(name: String?, teamId: String?): Long = withRealm { realm ->
        realm.where(org.ole.planet.myplanet.model.RealmTeamLog::class.java)
            .equalTo("type", "teamVisit")
            .equalTo("user", name)
            .equalTo("teamId", teamId)
            .count()
    }

    override suspend fun createNotification(effectiveTeamId: String, count: Int) = withRealm { realm ->
        realm.executeTransactionAsync {
            var notification = it.where(org.ole.planet.myplanet.model.RealmTeamNotification::class.java).equalTo("type", "chat").equalTo("parentId", effectiveTeamId).findFirst()
            if (notification == null) {
                notification = it.createObject(org.ole.planet.myplanet.model.RealmTeamNotification::class.java, java.util.UUID.randomUUID().toString())
                notification.parentId = effectiveTeamId
                notification.type = "chat"
            }
            notification?.lastCount = count
        }
    }

    override suspend fun getReplies(newsId: String?): List<RealmNews> = withRealm { realm ->
        realm.where(RealmNews::class.java)
            .sort("time", Sort.DESCENDING)
            .equalTo("replyTo", newsId, Case.INSENSITIVE)
            .findAll()
    }

    override suspend fun getLibrary(resourceId: String): RealmMyLibrary? = withRealm { realm ->
        realm.where(RealmMyLibrary::class.java)
            .equalTo("_id", resourceId)
            .findFirst()
    }

    override suspend fun deleteNews(news: RealmNews?) {
        if (news?.isValid == true) {
            withRealm { realm ->
                realm.executeTransaction {
                    news.deleteFromRealm()
                }
            }
        }
    }

    override suspend fun shareNews(news: RealmNews?, userId: String?, planetCode: String?, parentCode: String?, teamName: String?) {
        if (news?.isValid == true) {
            withRealm { realm ->
                realm.executeTransaction {
                    val array = gson.fromJson(news.viewIn, JsonArray::class.java)
                    val firstElement = array.get(0)
                    val obj = firstElement.asJsonObject
                    if (!obj.has("name")) {
                        obj.addProperty("name", teamName)
                    }
                    val ob = com.google.gson.JsonObject()
                    ob.addProperty("section", "community")
                    ob.addProperty("_id", "$planetCode@$parentCode")
                    ob.addProperty("sharedDate", java.util.Calendar.getInstance().timeInMillis)
                    array.add(ob)
                    news.sharedBy = userId
                    news.viewIn = gson.toJson(array)
                }
            }
        }
    }

    override suspend fun addLabelToNews(newsId: String?, label: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val news = it.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
                news?.labels?.add(label)
            }
        }
    }

    override suspend fun removeLabelFromNews(newsId: String?, label: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val news = it.where(RealmNews::class.java).equalTo("id", newsId).findFirst()
                news?.labels?.remove(label)
            }
        }
    }
}
