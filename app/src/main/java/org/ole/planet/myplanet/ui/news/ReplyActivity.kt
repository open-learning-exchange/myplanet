package org.ole.planet.myplanet.ui.news

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmList
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityReplyBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsItem
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.AdapterNews.OnNewsItemClickListener
import org.ole.planet.myplanet.ui.news.NewsActions
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getImagePath
import org.ole.planet.myplanet.utilities.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
open class ReplyActivity : AppCompatActivity(), OnNewsItemClickListener {
    private lateinit var activityReplyBinding: ActivityReplyBinding
    @Inject
    lateinit var databaseService: DatabaseService
    var id: String? = null
    private lateinit var newsAdapter: AdapterNews
    var user: RealmUserModel? = null

    private val viewModel: ReplyViewModel by viewModels()
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var imageList: RealmList<String>
    private var llImage: ViewGroup? = null
    private lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    private lateinit var mRealm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityReplyBinding = ActivityReplyBinding.inflate(layoutInflater)
        setContentView(activityReplyBinding.root)
        mRealm = Realm.getDefaultInstance()
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, activityReplyBinding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        title = "Reply"
        imageList = RealmList()
        id = intent.getStringExtra("id")
        user = userProfileDbHandler.userModel
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
        id ?: return
        lifecycleScope.launch {
            val (news, list) = viewModel.getNewsWithReplies(id)
            if (!mRealm.isClosed) {
                val parentItem = news?.let { NewsMapper.mapToNewsItem(this@ReplyActivity, mRealm, it, user, "") }
                val newsItems = list.mapNotNull { NewsMapper.mapToNewsItem(this@ReplyActivity, mRealm, it, user, "") }

                newsAdapter = AdapterNews(this@ReplyActivity, parentItem, this@ReplyActivity)
                newsAdapter.setFromLogin(intent.getBooleanExtra("fromLogin", false))
                newsAdapter.setNonTeamMember(intent.getBooleanExtra("nonTeamMember", false))
                newsAdapter.setImageList(imageList)
                newsAdapter.updateList(newsItems)
                activityReplyBinding.rvReply.adapter = newsAdapter
            }
        }
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

    override fun showReply(news: NewsItem, fromLogin: Boolean, nonTeamMember: Boolean) {
        startActivity(Intent(this, ReplyActivity::class.java).putExtra("id", news.id))
    }

    override fun addImage(llImage: ViewGroup?) {
        this.llImage = llImage
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        openFolderLauncher.launch(Intent.createChooser(intent, "Select Image"))
    }

    override fun onNewsItemClick(news: NewsItem) {}

    override fun onMemberSelected(news: NewsItem) {
        val userModel = mRealm.where(RealmUserModel::class.java).equalTo("id", news.userId).findFirst()
        val fragment = NewsActions.showMemberDetails(userModel, userProfileDbHandler) ?: return
        NavigationHelper.replaceFragment(
            supportFragmentManager,
            R.id.fragment_container,
            fragment,
            addToBackStack = true
        )
    }

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun getCurrentImageList(): List<String> {
        return imageList
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
        imageList.add(GsonUtils.gson.toJson(jsonObject))

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
            val ob = GsonUtils.gson.fromJson(img, JsonObject::class.java)
            val inflater = LayoutInflater.from(this).inflate(R.layout.image_thumb, llImage, false)
            val imgView = inflater.findViewById<ImageView>(R.id.thumb)
            Glide.with(this)
                .load(File(getString("imageUrl", ob)))
                .placeholder(R.drawable.ic_loading)
                .error(R.drawable.ic_loading)
                .into(imgView)
            llImage?.addView(inflater)
        }
        newsAdapter.setImageList(imageList)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        if (::mRealm.isInitialized && !mRealm.isClosed) mRealm.close()
        super.onDestroy()
    }

    override fun onEditNews(news: NewsItem) {
        NewsActions.showEditAlert(this, mRealm, news.id, true, user, this, null)
    }

    override fun onDeleteNews(news: NewsItem) {
        val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst()
        NewsActions.deletePost(mRealm, realmNews, mutableListOf(), "", this)
    }

    override fun onReplyNews(news: NewsItem) {
        NewsActions.showEditAlert(this, mRealm, news.id, false, user, this, null)
    }

    override fun onShareNews(news: NewsItem) {
         val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst() ?: return
         AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle(R.string.share_with_community)
                .setMessage(R.string.confirm_share_community)
                .setPositiveButton(R.string.yes) { _, _ ->
                    val array = GsonUtils.gson.fromJson(realmNews.viewIn, JsonArray::class.java)
                    val ob = JsonObject()
                    ob.addProperty("section", "community")
                    ob.addProperty("_id", user?.planetCode + "@" + user?.parentCode)
                    ob.addProperty("sharedDate", java.util.Calendar.getInstance().timeInMillis)
                    array.add(ob)
                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    realmNews.sharedBy = user?.id
                    realmNews.viewIn = GsonUtils.gson.toJson(array)
                    mRealm.commitTransaction()
                    Utilities.toast(this, getString(R.string.shared_to_community))
                    onDataChanged()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    override fun onAddLabel(news: NewsItem, view: View) {
         val usedLabels = news.labels?.toSet() ?: emptySet()
         val availableLabels = Constants.LABELS.filterValues { it !in usedLabels }

         val wrapper = androidx.appcompat.view.ContextThemeWrapper(this, R.style.CustomPopupMenu)
         val menu = android.widget.PopupMenu(wrapper, view)
         availableLabels.keys.forEach { labelName ->
             menu.menu.add(labelName)
         }
         menu.setOnMenuItemClickListener { menuItem ->
             val selectedLabel = Constants.LABELS[menuItem.title]
             val newsId = news.id
             if (selectedLabel != null && newsId != null) {
                 if (news.labels?.contains(selectedLabel) == true) {
                     return@setOnMenuItemClickListener true
                 }
                 val labelAdded = AtomicBoolean(false)
                 mRealm.executeTransactionAsync(Realm.Transaction { transactionRealm ->
                     val managedNews = transactionRealm.where(RealmNews::class.java)
                         .equalTo("id", newsId)
                         .findFirst()
                     managedNews?.labels?.add(selectedLabel)
                 }, Realm.Transaction.OnSuccess {
                     Utilities.toast(this, getString(R.string.label_added))
                     onDataChanged()
                 })
                 return@setOnMenuItemClickListener false
             }
             true
         }
         menu.show()
    }

    override fun onRemoveLabel(news: NewsItem, label: String) {
        val newsId = news.id ?: return
         mRealm.executeTransactionAsync(Realm.Transaction { transactionRealm ->
             val managedNews = transactionRealm.where(RealmNews::class.java)
                 .equalTo("id", newsId)
                 .findFirst()
             managedNews?.labels?.remove(label)
         }, Realm.Transaction.OnSuccess {
             onDataChanged()
         })
    }
}
