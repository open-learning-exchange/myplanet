package org.ole.planet.myplanet.ui.news

import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityNewsDetailBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtil
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class NewsDetailActivity : BaseActivity() {
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private lateinit var binding: ActivityNewsDetailBinding
    var news: RealmNews? = null
    lateinit var realm: Realm
    private val gson = Gson()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtil.setupEdgeToEdge(this, binding.root)
        setSupportActionBar(binding.toolbar)
        initActionBar()
        realm = databaseService.realmInstance
        val id = intent.getStringExtra("newsId")
        news = realm.where(RealmNews::class.java).equalTo("id", id).findFirst()
        if (news == null) {
            Utilities.toast(this, getString(R.string.new_not_available))
            finish()
            return
        }
        val user = userProfileDbHandler.userModel!!
        val userId = user.id
        realm.executeTransactionAsync {
            val newsLog: RealmNewsLog = it.createObject(RealmNewsLog::class.java, UUID.randomUUID().toString())
            newsLog.androidId = NetworkUtils.getUniqueIdentifier()
            newsLog.type = "news"
            newsLog.time = Date().time
            newsLog.userId = userId
        }
        initViews()
    }

    private fun initViews() {
        title = news?.userName
        var msg: String? = news?.message

        if (news?.imageUrls != null && (news?.imageUrls?.size ?: 0) > 0) {
            msg = loadLocalImage()
        } else {
            news?.imagesArray?.forEach {
                val ob = it.asJsonObject
                val resourceId = JsonUtils.getString("resourceId", ob.asJsonObject)
                val markDown = JsonUtils.getString("markdown", ob.asJsonObject)
                val library = realm.where(RealmMyLibrary::class.java).equalTo("_id", resourceId).findFirst()
                msg = msg?.replace(
                    markDown,
                    "<img style=\"float: right; padding: 10px 10px 10px 10px;\"  width=\"200px\" src=\"file://" + Utilities.SD_PATH + "/" + library?.id + "/" + library?.resourceLocalAddress + "\"/>",
                    false
                )
            }
            loadImage()
        }
        msg = msg?.replace(
            "\n",
            "<div/><br/><div style=\" word-wrap: break-word;page-break-after: always;  word-spacing: 2px;\" >"
        )
        binding.tvDetail.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
        binding.tvDetail.loadDataWithBaseURL(
            null,
            "<html><body><div style=\" word-wrap: break-word;  word-spacing: 2px;\" >$msg</div></body></html>",
            "text/html",
            "utf-8",
            null
        )
    }

    private fun loadLocalImage(): String? {
        var msg: String? = news?.message
        try {
            val imgObject = gson.fromJson(news?.imageUrls?.get(0), JsonObject::class.java)
            binding.img.visibility = View.VISIBLE
            Glide.with(this@NewsDetailActivity)
                .load(File(JsonUtils.getString("imageUrl", imgObject))).into(binding.img)
            news?.imageUrls?.forEach {
                val imageObject = gson.fromJson(it, JsonObject::class.java)
                msg += "<br/><img width=\"50%\" src=\"file://" + JsonUtils.getString(
                    "imageUrl", imageObject
                ) + "\"><br/>"
            }
        } catch (e: Exception) {
            loadImage()
        }
        return msg
    }

    private fun loadImage() {
        if ((news?.imagesArray?.size() ?: 0) > 0) {
            val ob = news?.imagesArray?.get(0)?.asJsonObject
            val resourceId = JsonUtils.getString("resourceId", ob?.asJsonObject)
            val library =
                realm.where(RealmMyLibrary::class.java).equalTo("_id", resourceId).findFirst()
            if (library != null) {
                Glide.with(this)
                    .load(File(Utilities.SD_PATH, library.id + "/" + library.resourceLocalAddress))
                    .into(binding.img)
                binding.img.visibility = View.VISIBLE
                return
            }
        }
        binding.img.visibility = View.GONE
    }

    override fun onDestroy() {
        if (::realm.isInitialized && !realm.isClosed) {
            realm.removeAllChangeListeners()
            realm.close()
        }
        super.onDestroy()
    }
}
