package org.ole.planet.myplanet.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.JsonObject
import io.realm.RealmList
import java.io.File
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnNewsItemClickListener
import org.ole.planet.myplanet.databinding.ImageThumbBinding
import org.ole.planet.myplanet.databinding.VideoThumbBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.ui.voices.ReplyActivity
import org.ole.planet.myplanet.ui.voices.VoicesActions
import org.ole.planet.myplanet.ui.voices.VoicesAdapter
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utils.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NavigationHelper

@RequiresApi(api = Build.VERSION_CODES.O)
abstract class BaseVoicesFragment : BaseContainerFragment(), OnNewsItemClickListener {
    lateinit var imageList: RealmList<String>
    lateinit var videoList: RealmList<String>
    @JvmField
    protected var llImage: ViewGroup? = null
    @JvmField
    protected var llVideo: ViewGroup? = null
    @JvmField
    protected var adapterNews: VoicesAdapter? = null
    lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    lateinit var openVideoLauncher: ActivityResultLauncher<Intent>
    private lateinit var replyActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageList = RealmList()
        videoList = RealmList()
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
        openVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val clipData = data?.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        processVideoUri(uri, result.resultCode)
                    }
                } else {
                    val uri = data?.data
                    processVideoUri(uri, result.resultCode)
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

    override fun onMemberSelected(userModel: RealmUser?) {
        if (!isAdded) return
        val handler = profileDbHandler
        lifecycleScope.launch {
            val fragment = VoicesActions.showMemberDetails(userModel, handler) ?: return@launch
            NavigationHelper.replaceFragment(
                requireActivity().supportFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
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

    override fun addVideo(llVideo: ViewGroup?) {
        this.llVideo = llVideo
        val openFolderIntent = FileUtils.openOleFolder(requireContext())
        openVideoLauncher.launch(openFolderIntent)
    }

    override fun getCurrentImageList(): RealmList<String>? {
        return if (::imageList.isInitialized) imageList else null
    }

    override fun getCurrentVideoList(): RealmList<String>? {
        return if (::videoList.isInitialized) videoList else null
    }

    private fun processImageUri(uri: Uri?, resultCode: Int) {
        if (uri == null) return

        var path: String? = getRealPathFromURI(requireActivity(), uri)
        if (TextUtils.isEmpty(path)) {
            path = FileUtils.getPathFromURI(requireActivity(), uri)
        }

        if (path.isNullOrEmpty()) return

        if (isImageAlreadyAdded(path)) {
            Toast.makeText(requireContext(), R.string.image_already_added, Toast.LENGTH_SHORT).show()
            return
        }

        val `object` = JsonObject()
        `object`.addProperty("imageUrl", path)
        `object`.addProperty("fileName", getFileNameFromUrl(path))
        imageList.add(JsonUtils.gson.toJson(`object`))

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

    private fun isImageAlreadyAdded(path: String): Boolean {
        return imageList.any { imageJson ->
            try {
                val imgObject = JsonUtils.gson.fromJson(imageJson, JsonObject::class.java)
                JsonUtils.getString("imageUrl", imgObject) == path
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun processVideoUri(uri: Uri?, resultCode: Int) {
        if (uri == null) return

        var path: String? = getRealPathFromURI(requireActivity(), uri)
        if (TextUtils.isEmpty(path)) {
            path = FileUtils.getPathFromURI(requireActivity(), uri)
        }

        if (path.isNullOrEmpty()) return

        // Check if the file is a video
        val extension = path.substringAfterLast('.', "").lowercase()
        val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp")
        if (extension !in videoExtensions) {
            Toast.makeText(requireContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show()
            return
        }

        if (isVideoAlreadyAdded(path)) {
            Toast.makeText(requireContext(), R.string.video_already_added, Toast.LENGTH_SHORT).show()
            return
        }

        val `object` = JsonObject()
        `object`.addProperty("videoUrl", path)
        `object`.addProperty("fileName", getFileNameFromUrl(path))
        videoList.add(JsonUtils.gson.toJson(`object`))

        try {
            llVideo?.visibility = View.VISIBLE
            val videoBinding = VideoThumbBinding.inflate(LayoutInflater.from(activity), llVideo, false)
            Glide.with(requireActivity())
                .asBitmap()
                .load(Uri.fromFile(File(path)))
                .frame(1000000) // 1 second in microseconds
                .into(videoBinding.thumb)
            llVideo?.addView(videoBinding.root)
            if (resultCode == 102) adapterNews?.setVideoList(videoList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isVideoAlreadyAdded(path: String): Boolean {
        return videoList.any { videoJson ->
            try {
                val vidObject = JsonUtils.gson.fromJson(videoJson, JsonObject::class.java)
                JsonUtils.getString("videoUrl", vidObject) == path
            } catch (e: Exception) {
                false
            }
        }
    }
}
