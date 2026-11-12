package org.ole.planet.myplanet.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnNewsItemClickListener
import org.ole.planet.myplanet.databinding.ImageThumbBinding
import org.ole.planet.myplanet.databinding.VideoThumbBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.voices.ReplyActivity
import org.ole.planet.myplanet.ui.voices.VoicesActions
import org.ole.planet.myplanet.ui.voices.VoicesAdapter
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.VoiceImagePreparer

abstract class BaseVoicesFragment : BaseContainerFragment(), OnNewsItemClickListener {
    lateinit var imageList: MutableList<String>
    lateinit var videoList: MutableList<String>

    protected var llImage: ViewGroup? = null
    @JvmField
    protected var llVideo: ViewGroup? = null
    @JvmField
    protected var adapterNews: VoicesAdapter? = null
    lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    lateinit var openVideoLauncher: ActivityResultLauncher<Intent>
    lateinit var openMediaLauncher: ActivityResultLauncher<Intent>
    private lateinit var replyActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageList = mutableListOf()
        videoList = mutableListOf()
        openFolderLauncher = registerMediaPicker(MediaKind.IMAGE)
        openVideoLauncher = registerMediaPicker(MediaKind.VIDEO)
        openMediaLauncher = registerMediaPicker(MediaKind.ANY)
        replyActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val newsId = result.data?.getStringExtra("newsId")
                newsId.let { adapterNews?.updateReplyBadge(it) }
                val recyclerView = view?.findViewById<RecyclerView>(R.id.rv_news)
                val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
                val scrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
                val scrollOffset = layoutManager?.findViewByPosition(scrollPosition)?.top ?: 0
                adapterNews?.submitList(adapterNews?.currentList?.toList()) {
                    layoutManager?.scrollToPositionWithOffset(scrollPosition, scrollOffset)
                }
            }
        }
    }

    override fun onDataChanged() {
        val recyclerView = view?.findViewById<RecyclerView>(R.id.rv_news)
        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        val scrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
        val scrollOffset = layoutManager?.findViewByPosition(scrollPosition)?.top ?: 0
        adapterNews?.submitList(adapterNews?.currentList?.toList()) {
            layoutManager?.scrollToPositionWithOffset(scrollPosition, scrollOffset)
        }
    }

    override fun onReplyPosted(newsId: String?) {
        adapterNews?.updateReplyBadge(newsId)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) homeItemClickListener = context
    }

    override fun showReply(news: News?, fromLogin: Boolean, nonTeamMember: Boolean) {
        if (news != null) {
            val intent = Intent(activity, ReplyActivity::class.java).putExtra("id", news.id)
                .putExtra("fromLogin", fromLogin)
                .putExtra("nonTeamMember", nonTeamMember)
            replyActivityLauncher.launch(intent)
        }
    }

    override fun onMemberSelected(userModel: UserEntity?) {
        if (!isAdded) return

        viewLifecycleOwner.lifecycleScope.launch {
            val fragment = VoicesActions.showMemberDetails(userModel) ?: return@launch
            if (!isAdded) return@launch
            FragmentNavigator.replaceFragment(
                requireActivity().supportFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
    }

    abstract fun setData(list: List<News?>?)
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

    override fun getCurrentImageList(): List<String>? {
        return if (::imageList.isInitialized) imageList else null
    }

    override fun getCurrentVideoList(): List<String>? {
        return if (::videoList.isInitialized) videoList else null
    }

    override fun restoreImages(snapshot: List<String>) {
        if (::imageList.isInitialized) imageList.retainAll(snapshot.toSet())
    }

    private enum class MediaKind { IMAGE, VIDEO, ANY }

    private fun registerMediaPicker(accept: MediaKind): ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data
            val clipData = data?.clipData
            val uris = if (clipData != null) {
                (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
            } else {
                listOf(data?.data)
            }
            if (view == null) return@registerForActivityResult
            handlePickedMedia(uris.filterNotNull(), accept, result.resultCode)
        }

    private fun handlePickedMedia(uris: List<Uri>, accept: MediaKind, resultCode: Int) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            for (uri in uris) {
                if (!isAdded) return@launch
                val isVideo = appContext.contentResolver.getType(uri)?.startsWith("video/")
                    ?: (FileUtils.getFileNameFromUrl(uri.toString()).substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS)
                when {
                    isVideo && accept != MediaKind.IMAGE -> addVideoUri(appContext, uri, resultCode)
                    !isVideo && accept != MediaKind.VIDEO -> addImageUri(appContext, uri, resultCode)
                    accept == MediaKind.VIDEO ->
                        Toast.makeText(requireContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show()
                    else ->
                        Toast.makeText(requireContext(), R.string.voice_image_unreadable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun addImageUri(appContext: Context, uri: Uri, resultCode: Int) {
        val prepared = withContext(dispatcherProvider.io) {
            VoiceImagePreparer.prepare(appContext, uri, timeProvider)
        }
        if (!isAdded) return
        when (prepared) {
            is VoiceImagePreparer.Result.Ready -> addImagePath(prepared.path, resultCode)
            VoiceImagePreparer.Result.GifTooLarge ->
                Toast.makeText(requireContext(), R.string.voice_image_gif_too_large, Toast.LENGTH_SHORT).show()
            VoiceImagePreparer.Result.Unreadable ->
                Toast.makeText(requireContext(), R.string.voice_image_unreadable, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun addVideoUri(appContext: Context, uri: Uri, resultCode: Int) {
        // External files dir, not cacheDir: the cache can be evicted before upload, and
        // ResourceViewerActivity only plays full paths under external storage roots.
        val path = withContext(dispatcherProvider.io) {
            val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, VOICE_VIDEO_DIR).apply { mkdirs() }
            FileUtils.resolveUriToPath(appContext, uri, dir)
        }
        if (!isAdded) return
        if (path.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show()
            return
        }
        addVideoPath(path, resultCode)
    }

    private fun addImagePath(path: String?, resultCode: Int) {
        if (path.isNullOrEmpty()) return

        if (News.containsImagePath(imageList, path)) {
            Toast.makeText(requireContext(), R.string.image_already_added, Toast.LENGTH_SHORT).show()
            return
        }

        imageList.add(News.createImageListEntry(path))

        try {
            llImage?.visibility = View.VISIBLE
            val imageBinding = ImageThumbBinding.inflate(LayoutInflater.from(activity), llImage, false)
            Glide.with(requireActivity())
                .load(File(path))
                .into(imageBinding.thumb)
            llImage?.addView(imageBinding.root)
            if (resultCode == 102) adapterNews?.setImageList(imageList)
        } catch (e: Exception) {
            Log.w(TAG, "addImagePath failed", e)
        }
    }

    companion object {
        private const val TAG = "BaseVoicesFragment"
        private const val VIDEO_THUMB_FRAME_US = 1_000_000L
        private const val VOICE_VIDEO_DIR = "voice_videos"
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp")
    }

    private fun addVideoPath(path: String, resultCode: Int) {
        if (isVideoAlreadyAdded(path)) {
            Toast.makeText(requireContext(), R.string.video_already_added, Toast.LENGTH_SHORT).show()
            return
        }

        val entry = JsonObject()
        entry.addProperty("videoUrl", path)
        entry.addProperty("fileName", FileUtils.getFileNameFromUrl(path))
        videoList.add(GsonUtils.gson.toJson(entry))

        try {
            llVideo?.visibility = View.VISIBLE
            val videoBinding = VideoThumbBinding.inflate(LayoutInflater.from(activity), llVideo, false)
            Glide.with(requireActivity())
                .asBitmap()
                .load(File(path))
                .frame(VIDEO_THUMB_FRAME_US)
                .into(videoBinding.thumb)
            llVideo?.addView(videoBinding.root)
            if (resultCode == 102) adapterNews?.setVideoList(videoList)
        } catch (e: Exception) {
            Log.w(TAG, "addVideoPath failed", e)
        }
    }

    private fun isVideoAlreadyAdded(path: String): Boolean {
        return videoList.any { videoJson ->
            try {
                val vidObject = GsonUtils.gson.fromJson(videoJson, JsonObject::class.java)
                GsonUtils.getString("videoUrl", vidObject) == path
            } catch (e: Exception) {
                false
            }
        }
    }
}
