package org.ole.planet.myplanet.ui.news

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_news_detail.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utilities.Utilities

class NewsDetailActivity : BaseActivity() {
    lateinit var realm: Realm;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_detail)
        setSupportActionBar()
        setSupportActionBar(toolbar)
        realm = DatabaseService(this).realmInstance
        var id = intent.getStringExtra("newsId")
        var news = realm.where(RealmNews::class.java).equalTo("id", id).findFirst()
        if(news == null){
            Utilities.toast(this, "New not available")
            finish()
            return
        }

    }
}
