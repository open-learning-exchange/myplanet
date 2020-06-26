package org.ole.planet.myplanet.ui.news

import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_news_detail.*
import kotlinx.android.synthetic.main.content_news_detail.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.base.BaseNewsAdapter.ViewHolderNews
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.util.*

class NewsDetailActivity : BaseActivity() {
    var news: RealmNews? = null
    lateinit var realm: Realm
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_detail)
        setSupportActionBar(toolbar)
        initActionBar()
        realm = DatabaseService(this).realmInstance
        var id = intent.getStringExtra("newsId")
        news = realm.where(RealmNews::class.java).equalTo("id", id).findFirst()
        if (news == null) {
            Utilities.toast(this, "New not available")
            finish()
            return
        }
        var user = UserProfileDbHandler(this).userModel
        var userId = user.id
        realm.executeTransactionAsync {
            var newsLog: RealmNewsLog = it.createObject(RealmNewsLog::class.java, UUID.randomUUID().toString())
            newsLog.androidId = NetworkUtils.getMacAddr()
            newsLog.type = "news"
            newsLog.time = Date().time
            if (user != null)
                newsLog.userId = userId
        }
        initViews()

    }

    private fun initViews() {
        title = news?.userName
        var msg: String = news!!.message

        if (news!!.imageUrls != null && news!!.imageUrls.size > 0) {
            msg = loadLocalImage();
        } else {
            news?.imagesArray?.forEach {
                val ob = it.asJsonObject
                val resourceId = JsonUtils.getString("resourceId", ob.asJsonObject)
                val markDown = JsonUtils.getString("markdown", ob.asJsonObject)
                val library = realm.where(RealmMyLibrary::class.java).equalTo("_id", resourceId).findFirst()
                msg = msg.replace(markDown, "<img style=\"float: right; padding: 10px 10px 10px 10px;\"  width=\"200px\" src=\"file://" + Utilities.SD_PATH + "/" + library?.id + "/" + library?.resourceLocalAddress + "\"/>", false)
            }
            loadImage()
        }
        msg = msg.replace("\n", "<div/><br/><div style=\" word-wrap: break-word;page-break-after: always;  word-spacing: 20px;\" >")
        tv_detail.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN;
        tv_detail.loadDataWithBaseURL(null, "<html><body><div style=\" word-wrap: break-word;  word-spacing: 20px;\" >$msg</div></body></html>", "text/html", "utf-8", null)
    }

    private fun loadLocalImage(): String {
        var msg: String = news!!.message
        try {
            val imgObject = Gson().fromJson(news!!.imageUrls[0], JsonObject::class.java)
            img.visibility = View.VISIBLE
            Glide.with(this@NewsDetailActivity).load(File(JsonUtils.getString("imageUrl", imgObject))).into(img)
            news!!.imageUrls.forEach {
                val imgObject = Gson().fromJson(it, JsonObject::class.java)
                msg += "<br/><img width=\"50%\" src=\"file://" + JsonUtils.getString("imageUrl", imgObject) + "\"><br/>"
            }
        } catch (e: Exception) {
            loadImage()
        }
        return msg
    }

    private fun loadImage() {
        if (news?.imagesArray!!.size() > 0) {
            val ob = news!!.imagesArray[0].asJsonObject
            val resourceId = JsonUtils.getString("resourceId", ob.asJsonObject)
            val library = realm.where(RealmMyLibrary::class.java).equalTo("_id", resourceId).findFirst()
            if (library != null) {
                Glide.with(this)
                        .load(File(Utilities.SD_PATH, library.id + "/" + library.resourceLocalAddress))
                        .into(img)
                img.visibility = View.VISIBLE
                return
            }
        }
        img.visibility = View.GONE
    }
}
