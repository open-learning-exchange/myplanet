package org.ole.planet.myplanet.ui.news

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

class NewsViewModel : ViewModel() {
    private val _newsList = MutableLiveData<List<RealmNews?>>()
    val newsList: LiveData<List<RealmNews?>> = _newsList

    fun loadNews(realm: Realm, user: RealmUserModel?, settings: SharedPreferences) {
        val results = realm.where(RealmNews::class.java)
            .sort("time", Sort.DESCENDING)
            .isEmpty("replyTo")
            .equalTo("docType", "message", Case.INSENSITIVE)
            .findAllAsync()

        results.addChangeListener { res ->
            _newsList.postValue(filterNewsList(res, user, settings))
        }
        _newsList.value = filterNewsList(results, user, settings)
    }

    fun addNews(news: RealmNews?) {
        news ?: return
        val current = _newsList.value?.toMutableList() ?: mutableListOf()
        current.add(0, news)
        _newsList.value = current
    }

    fun getAllNews(realm: Realm, user: RealmUserModel?, settings: SharedPreferences): List<RealmNews?> {
        val allNews: RealmResults<RealmNews> = realm.where(RealmNews::class.java)
            .isEmpty("replyTo")
            .equalTo("docType", "message", Case.INSENSITIVE)
            .findAll()
        return filterNewsList(allNews, user, settings)
    }

    private fun filterNewsList(results: Iterable<RealmNews>, user: RealmUserModel?, settings: SharedPreferences): List<RealmNews?> {
        val filteredList: MutableList<RealmNews?> = ArrayList()
        for (news in results) {
            if (news.viewableBy.equals("community", ignoreCase = true)) {
                filteredList.add(news)
                continue
            }
            if (!news.viewIn.isNullOrEmpty()) {
                val ar = Gson().fromJson(news.viewIn, JsonArray::class.java)
                for (e in ar) {
                    val ob = e.asJsonObject
                    var userId = "${'$'}{user?.planetCode}@${'$'}{user?.parentCode}"
                    if (userId.isEmpty() || userId == "@") {
                        val planet = settings.getString("planetCode", "") ?: ""
                        val parent = settings.getString("parentCode", "") ?: ""
                        userId = "${'$'}planet@${'$'}parent"
                    }
                    if (ob != null && ob.has("_id") && ob["_id"].asString.equals(userId, ignoreCase = true)) {
                        filteredList.add(news)
                        break
                    }
                }
            }
        }
        return filteredList
    }
}
