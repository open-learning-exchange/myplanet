package org.ole.planet.myplanet.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.JsonObject
import io.realm.RealmList
import java.io.File
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ImageThumbBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.NewsAdapter
import org.ole.planet.myplanet.ui.news.NewsAdapter.OnNewsItemClickListener
import org.ole.planet.myplanet.ui.news.NewsActions
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utilities.GsonUtils

@RequiresApi(api = Build.VERSION_CODES.O)
abstract class BaseNewsFragment : BaseContainerFragment(), OnNewsItemClickListener {
    lateinit var imageList: RealmList<String>
    @JvmField
    protected var llImage: ViewGroup? = null
    @JvmField
    protected var adapterNews: NewsAdapter? = null
    lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    private lateinit var replyActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageList = RealmList()
        openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val clipData = data?.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        processImageUri(uri, result.resultCode)
                    }
                } else {
                    val uri = data?.data
                    processImageUri(uri, result.resultCode)
                }
            }
        }
        replyActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val newsId = result.data?.getStringExtra("newsId")
                newsId.let { adapterNews?.updateReplyBadge(it) }
                adapterNews?.refreshCurrentItems()
            }
        }
    }

    override fun onDataChanged() {
        adapterNews?.refreshCurrentItems()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) homeItemClickListener = context
    }

    override fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean) {
        if (news != null) {
            val intent = Intent(activity, ReplyActivity::class.java).putExtra("id", news.id)
                .putExtra("fromLogin", fromLogin)
                .putExtra("nonTeamMember", nonTeamMember)
            replyActivityLauncher.launch(intent)
        }
    }

    override fun onMemberSelected(userModel: RealmUserModel?) {
        if (!isAdded) return
        val handler = profileDbHandler
        val fragment = NewsActions.showMemberDetails(userModel, handler) ?: return
        NavigationHelper.replaceFragment(
            requireActivity().supportFragmentManager,
            R.id.fragment_container,
            fragment,
            addToBackStack = true
        )
    }

    abstract fun setData(list: List<RealmNews?>?)
    fun showNoData(v: View?, count: Int?, source: String) {
        count?.let { BaseRecyclerFragment.showNoData(v, it, source) }
    }


    fun changeLayoutManager(orientation: Int, recyclerView: RecyclerView) {
        activity?.let { act ->
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                recyclerView.layoutManager = GridLayoutManager(act, 2)
            } else {
                recyclerView.layoutManager = LinearLayoutManager(act)
            }
        }
    }

    override fun addImage(llImage: ViewGroup?) {
        this.llImage = llImage
        val openFolderIntent = FileUtils.openOleFolder(requireContext())
        openFolderLauncher.launch(openFolderIntent)
    }

    override fun getCurrentImageList(): RealmList<String>? {
        return if (::imageList.isInitialized) imageList else null
    }

    private fun processImageUri(uri: Uri?, resultCode: Int) {
        if (uri == null) return

        var path: String? = getRealPathFromURI(requireActivity(), uri)
        if (TextUtils.isEmpty(path)) {
            path = FileUtils.getPathFromURI(requireActivity(), uri)
        }

        if (path.isNullOrEmpty()) return

        val `object` = JsonObject()
        `object`.addProperty("imageUrl", path)
        `object`.addProperty("fileName", getFileNameFromUrl(path))
        imageList.add(GsonUtils.gson.toJson(`object`))

        try {
            llImage?.visibility = View.VISIBLE
            val imageBinding = ImageThumbBinding.inflate(LayoutInflater.from(activity), llImage, false)
            Glide.with(requireActivity())
                .load(File(path))
                .into(imageBinding.thumb)
            llImage?.addView(imageBinding.root)
            if (resultCode == 102) adapterNews?.setImageList(imageList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
