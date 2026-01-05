package org.ole.planet.myplanet.ui.voices

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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.RealmList
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.databinding.ActivityReplyBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.voices.NewsActions
import org.ole.planet.myplanet.ui.voices.NewsAdapter.OnNewsItemClickListener
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getImagePath
import org.ole.planet.myplanet.utilities.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NavigationHelper
import org.ole.planet.myplanet.utilities.SharedPrefManager

@AndroidEntryPoint
open class ReplyActivity : AppCompatActivity(), OnNewsItemClickListener {
    private lateinit var activityReplyBinding: ActivityReplyBinding
    @Inject
    lateinit var databaseService: DatabaseService
    var id: String? = null
    private lateinit var newsAdapter: NewsAdapter
    var user: RealmUserModel? = null

    private val viewModel: ReplyViewModel by viewModels()
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var userRepository: org.ole.planet.myplanet.repository.UserRepository
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    @Inject
    lateinit var voicesRepository: VoicesRepository
    @Inject
    lateinit var teamsRepository: TeamsRepository

    private lateinit var imageList: RealmList<String>
    private var llImage: ViewGroup? = null
    private lateinit var openFolderLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityReplyBinding = ActivityReplyBinding.inflate(layoutInflater)
        setContentView(activityReplyBinding.root)
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
            databaseService.withRealm { realm ->
                newsAdapter = NewsAdapter(this@ReplyActivity, user, news, "", null, userProfileDbHandler, lifecycleScope, userRepository, voicesRepository, teamsRepository)
                newsAdapter.sharedPrefManager = sharedPrefManager
                newsAdapter.setListener(this@ReplyActivity)
                newsAdapter.setmRealm(realm)
                newsAdapter.setFromLogin(intent.getBooleanExtra("fromLogin", false))
                newsAdapter.setNonTeamMember(intent.getBooleanExtra("nonTeamMember", false))
                newsAdapter.setImageList(imageList)
                newsAdapter.updateList(list)
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

    override fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean) {
        startActivity(Intent(this, ReplyActivity::class.java).putExtra("id", news?.id))
    }

    override fun addImage(llImage: ViewGroup?) {
        this.llImage = llImage
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        openFolderLauncher.launch(Intent.createChooser(intent, "Select Image"))
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun onMemberSelected(userModel: RealmUserModel?) {
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

    override fun getCurrentImageList(): RealmList<String> {
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
        imageList.add(JsonUtils.gson.toJson(jsonObject))

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
            val ob = JsonUtils.gson.fromJson(img, JsonObject::class.java)
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
        super.onDestroy()
    }
}
