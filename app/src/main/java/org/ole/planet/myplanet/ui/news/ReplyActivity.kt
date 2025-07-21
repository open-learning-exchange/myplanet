package org.ole.planet.myplanet.ui.news

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import java.io.File
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityReplyBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.news.AdapterNews.OnNewsItemClickListener
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtil
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getImagePath
import org.ole.planet.myplanet.utilities.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utilities.JsonUtils.getString

@AndroidEntryPoint
open class ReplyActivity : AppCompatActivity(), OnNewsItemClickListener {
    private lateinit var activityReplyBinding: ActivityReplyBinding
    lateinit var mRealm: Realm
    var id: String? = null
    private lateinit var newsAdapter: AdapterNews
    private val gson = Gson()
    var user: RealmUserModel? = null
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private lateinit var imageList: RealmList<String>
    private var llImage: LinearLayout? = null
    private lateinit var openFolderLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityReplyBinding = ActivityReplyBinding.inflate(layoutInflater)
        setContentView(activityReplyBinding.root)
        EdgeToEdgeUtil.setupEdgeToEdge(this, activityReplyBinding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        mRealm = DatabaseService(this).realmInstance
        title = "Reply"
        imageList = RealmList()
        id = intent.getStringExtra("id")
        user = UserProfileDbHandler(this).userModel
        activityReplyBinding.rvReply.layoutManager = LinearLayoutManager(this)
        activityReplyBinding.rvReply.isNestedScrollingEnabled = false
        showData(id)
        openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val url = result.data?.data
                handleImageSelection(url)
            }
        }
        val resultIntent = Intent().putExtra("newsId", id)
        setResult(Activity.RESULT_OK, resultIntent)
    }

    private fun showData(id: String?) {
        val news = mRealm.where(RealmNews::class.java).equalTo("id", id).findFirst()
        val list: List<RealmNews?> = mRealm.where(RealmNews::class.java).sort("time", Sort.DESCENDING).equalTo("replyTo", id, Case.INSENSITIVE).findAll()
        newsAdapter = AdapterNews(this, list.toMutableList(), user, news, "", null, userProfileDbHandler)
        newsAdapter.setListener(this)
        newsAdapter.setmRealm(mRealm)
        newsAdapter.setFromLogin(intent.getBooleanExtra("fromLogin", false))
        newsAdapter.setNonTeamMember(intent.getBooleanExtra("nonTeamMember", false))
        activityReplyBinding.rvReply.adapter = newsAdapter
    }

    override fun onResume() {
        super.onResume()
        refreshData()

    }
    private fun refreshData() {
        id?.let { showData(it) }
    }

    override fun onDataChanged() {
        refreshData()
    }

    override fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean) {
        startActivity(Intent(this, ReplyActivity::class.java).putExtra("id", news?.id))
    }

    override fun addImage(llImage: LinearLayout?) {
        this.llImage = llImage
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        openFolderLauncher.launch(Intent.createChooser(intent, "Select Image"))
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun handleImageSelection(url: Uri?) {
        if (url == null) {
            return
        }

        var path: String? = getRealPathFromURI(this, url)
        if (TextUtils.isEmpty(path)) {
            path = getImagePath(this, url)
        }

        if (path == null) {
            return
        }

        val jsonObject = JsonObject()
        jsonObject.addProperty("imageUrl", path)
        jsonObject.addProperty("fileName", getFileNameFromUrl(path))
        imageList.add(gson.toJson(jsonObject))

        try {
            showSelectedImages()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSelectedImages() {
        llImage?.removeAllViews()
        llImage?.visibility = View.VISIBLE
        for (img in imageList) {
            val ob = gson.fromJson(img, JsonObject::class.java)
            val inflater = LayoutInflater.from(this).inflate(R.layout.image_thumb, llImage, false)
            val imgView = inflater.findViewById<ImageView>(R.id.thumb)
            Glide.with(this).load(File(getString("imageUrl", ob))).into(imgView)
            llImage?.addView(inflater)
        }
        newsAdapter.setImageList(imageList)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
