package org.ole.planet.myplanet.ui.news

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_news_detail.*
import kotlinx.android.synthetic.main.content_news_detail.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File

class NewsDetailActivity : BaseActivity() {
    var news: RealmNews? = null;
    lateinit var realm: Realm;
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
        title = news?.userName
        tv_detail.text = news?.message;

        val imageUrl = news?.imageUrl
        if (TextUtils.isEmpty(imageUrl)) {
            loadImage()
        } else {
            try {
                img.visibility = View.VISIBLE
                Utilities.log("image url " + news?.imageUrl)
                Glide.with(this)
                        .load(File(imageUrl))
                        .into(img)
            } catch (e: Exception) {
                loadImage()
                e.printStackTrace()
            }

        }
    }

    private fun loadImage() {
        if (news?.images != null && news?.images!!.size > 0) {
            val library = realm.where(RealmMyLibrary::class.java).equalTo("_id", news!!.images[0]).findFirst()
            if (library != null) {
                Glide.with(this)
                        .load(File(Utilities.SD_PATH, library!!.getId() + "/" + library!!.getResourceLocalAddress()))
                        .into(img)
                img.visibility = View.VISIBLE
                return
            }
        }
        img.visibility = View.GONE
    }
}
