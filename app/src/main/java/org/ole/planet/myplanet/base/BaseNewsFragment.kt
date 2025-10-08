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
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.RealmList
import java.io.File
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ImageThumbBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.news.AdapterNews.OnNewsItemClickListener
import org.ole.planet.myplanet.ui.news.NewsActions
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utilities.JsonUtils.getString

@RequiresApi(api = Build.VERSION_CODES.O)
abstract class BaseNewsFragment : BaseContainerFragment(), OnNewsItemClickListener {
    lateinit var imageList: RealmList<String>
    @JvmField
    protected var llImage: LinearLayout? = null
    @JvmField
    protected var adapterNews: AdapterNews? = null
    lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    private lateinit var replyActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageList = RealmList()
        profileDbHandler = UserProfileDbHandler(requireContext())
        openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                var path: String?
                val url: Uri? = data?.data
                path = getRealPathFromURI(requireActivity(), url)
                if (TextUtils.isEmpty(path)) {
                    path = FileUtils.getPathFromURI(requireActivity(), url)
                }
                val `object` = JsonObject()
                `object`.addProperty("imageUrl", path)
                `object`.addProperty("fileName", getFileNameFromUrl(path))
                imageList.add(Gson().toJson(`object`))
                try {
                    llImage?.removeAllViews()
                    llImage?.visibility = View.VISIBLE
                    for (img in imageList) {
                        val ob = Gson().fromJson(img, JsonObject::class.java)
                        val imageBinding = ImageThumbBinding.inflate(LayoutInflater.from(activity), llImage, false)
                        Glide.with(requireActivity())
                            .load(File(getString("imageUrl", ob)))
                            .into(imageBinding.thumb)
                        llImage?.addView(imageBinding.root)
                    }
                        if (result.resultCode == 102) adapterNews?.setImageList(imageList)
                    } catch (e: Exception) {
                        e.printStackTrace()
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

    override fun onDestroy() {
        profileDbHandler?.onDestroy()
        profileDbHandler = null
        super.onDestroy()
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
        val handler = profileDbHandler ?: return
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

    override fun addImage(llImage: LinearLayout?) {
        this.llImage = llImage
        val openFolderIntent = FileUtils.openOleFolder(requireContext())
        openFolderLauncher.launch(openFolderIntent)
    }

    override fun getCurrentImageList(): RealmList<String>? {
        return if (::imageList.isInitialized) imageList else null
    }
}
