package org.ole.planet.myplanet.base

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BasePermissionActivity.Companion.hasInstallPermission
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.service.UserProfileDbHandler.Companion.KEY_RESOURCE_DOWNLOAD
import org.ole.planet.myplanet.utilities.NavigationHelper
import org.ole.planet.myplanet.ui.viewer.WebViewActivity
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.ResourceOpener
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
abstract class BaseContainerFragment : BaseResourceFragment() {
    private var timesRated: TextView? = null
    var rating: TextView? = null
    private var ratingBar: AppCompatRatingBar? = null
    private val installUnknownSourcesRequestCode = 112
    private var hasInstallPermissionValue = false
    private var currentLibrary: RealmMyLibrary? = null
    private var installApkLauncher: ActivityResultLauncher<Intent>? = null
    lateinit var prefData: SharedPrefManager
    private var pendingAutoOpenLibrary: RealmMyLibrary? = null
    private var shouldAutoOpenAfterDownload = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasInstallPermissionValue = hasInstallPermission(requireContext())
        if (!BuildConfig.LITE) {
            installApkLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    currentLibrary?.let {
                        installApk(it)
                        currentLibrary = null
                    }
                }
            }
        }
        prefData = SharedPrefManager(requireContext())
    }

    fun setRatings(`object`: JsonObject?) {
        if (`object` != null) {
            CourseRatingUtils.showRating(requireContext(), `object`, rating, timesRated, ratingBar)
        }
    }
    fun getUrlsAndStartDownload(lib: List<RealmMyLibrary?>, urls: ArrayList<String>) {
        for (library in lib) {
            val url = UrlUtils.getUrl(library)
            if (!FileUtils.checkFileExist(requireContext(), url) && !TextUtils.isEmpty(url)) {
                urls.add(url)
            }
        }
        if (urls.isNotEmpty()) {
            startDownload(urls)
        }
    }
    fun startDownloadWithAutoOpen(urls: ArrayList<String>, libraryToOpen: RealmMyLibrary? = null) {
        if (libraryToOpen != null) {
            pendingAutoOpenLibrary = libraryToOpen
            shouldAutoOpenAfterDownload = true
        }
        startDownload(urls)
    }
    override fun onDownloadComplete() {
        super.onDownloadComplete()
        if (shouldAutoOpenAfterDownload && pendingAutoOpenLibrary != null) {
            pendingAutoOpenLibrary?.let { library ->
                shouldAutoOpenAfterDownload = false
                pendingAutoOpenLibrary = null

                val isDownloaded = if (library.mediaType == "HTML") {
                    val directory = File(context?.getExternalFilesDir(null), "ole/${library.resourceId}")
                    val indexFile = File(directory, "index.html")
                    indexFile.exists()
                } else {
                    library.isResourceOffline() || FileUtils.checkFileExist(requireContext(), UrlUtils.getUrl(library))
                }

                if (isDownloaded) {
                    openResource(library)
                }
            }
        }
    }
    fun initRatingView(type: String?, id: String?, title: String?, listener: OnRatingChangeListener?) {
        timesRated = requireView().findViewById(R.id.times_rated)
        rating = requireView().findViewById(R.id.tv_rating)
        ratingBar = requireView().findViewById(R.id.rating_bar)
        ratingBar?.apply {
            setOnTouchListener { view, e: MotionEvent ->
                if (e.action == MotionEvent.ACTION_UP) {
                    view.performClick()
                }
                true
            }
            val userModel = profileDbHandler.userModel
            if (userModel?.isGuest() == false) {
                setOnClickListener {
                    homeItemClickListener?.showRatingDialog(type, id, title, listener)
                }
            }
        }
    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
    }

    private fun dismissProgressDialog() {
        try {
            if (prgDialog.isShowing()) {
                prgDialog.dismiss()
            }
        } catch (e: UninitializedPropertyAccessException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun openResource(items: RealmMyLibrary) {
        dismissProgressDialog()
        if (items.mediaType == "HTML") {
            openHtmlResource(items)
        } else {
            openNonHtmlResource(items)
        }
    }

    private fun openHtmlResource(items: RealmMyLibrary) {
        val directory = File(context?.getExternalFilesDir(null), "ole/${items.resourceId}")
        val indexFile = File(directory, "index.html")

        if (indexFile.exists()) {
            val intent = Intent(activity, WebViewActivity::class.java)
            intent.putExtra("RESOURCE_ID", items.id)
            intent.putExtra("LOCAL_ADDRESS", items.resourceLocalAddress)
            intent.putExtra("title", items.title)
            startActivity(intent)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val resource = items.resourceId?.let { resourcesRepository.getLibraryItemByResourceId(it) }
            val downloadUrls = resource?.attachments
                ?.mapNotNull { attachment ->
                    attachment.name?.let { name ->
                        createAttachmentDir(items.resourceId, name)
                        UrlUtils.getUrl("${items.resourceId}", name)
                    }
                }
                ?.toCollection(ArrayList()) ?: arrayListOf()

            if (downloadUrls.isNotEmpty()) {
                startDownloadWithAutoOpen(downloadUrls, items)
            } else {
                val errorMessage = when {
                    resource == null -> getString(R.string.resource_not_found_in_database)
                    resource.attachments.isNullOrEmpty() -> getString(R.string.resource_has_no_attachments)
                    else -> getString(R.string.unable_to_download_resource)
                }
                Utilities.toast(activity, errorMessage)
            }
        }
    }

    private fun createAttachmentDir(resourceId: String?, name: String) {
        val baseDir = File(context?.getExternalFilesDir(null), "ole/$resourceId")
        val lastSlashIndex = name.lastIndexOf('/')
        if (lastSlashIndex > 0) {
            val dirPath = name.substring(0, lastSlashIndex)
            File(baseDir, dirPath).mkdirs()
        }
    }

    private fun openNonHtmlResource(items: RealmMyLibrary) {
        viewLifecycleOwner.lifecycleScope.launch {
            val matchingItems = items.resourceLocalAddress?.let {
                resourcesRepository.getLibraryItemsByLocalAddress(it)
            } ?: emptyList()

            val offlineItem = matchingItems.firstOrNull { it.isResourceOffline() }
            if (offlineItem != null) {
                ResourceOpener.openFileType(requireActivity(), offlineItem, "offline", profileDbHandler)
                return@launch
            }

            when {
                items.isResourceOffline() -> ResourceOpener.openFileType(
                    requireActivity(), items, "offline", profileDbHandler
                )
                FileUtils.getFileExtension(items.resourceLocalAddress) == "mp4" -> ResourceOpener.openFileType(
                    requireActivity(), items, "online", profileDbHandler
                )
                else -> {
                    val arrayList = arrayListOf(UrlUtils.getUrl(items))
                    startDownloadWithAutoOpen(arrayList, items)
                    profileDbHandler.setResourceOpenCount(items, KEY_RESOURCE_DOWNLOAD)
                }
            }
        }
    }

    private fun installApk(items: RealmMyLibrary) {
        if (BuildConfig.LITE) return
        currentLibrary = items
        val directory = File(requireContext().getExternalFilesDir(null).toString() + "/ole" + "/" + items.id)
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw RuntimeException("Failed to create directory: " + directory.absolutePath)
            }
        }
        val apkFile = items.resourceLocalAddress?.let { File(directory, it) }
        if (apkFile != null) {
            if (!apkFile.exists()) {
                Utilities.toast(activity,"APK file not found")
                return
            }
        }
        val uri = apkFile?.let {
            FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", it)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            if (hasInstallPermission(requireContext())) {
                startActivity(intent)
            } else {
                requestInstallPermission()
            }
        } else {
            Utilities.toast(activity,"No app to handle the installation")
        }
    }

    private fun requestInstallPermission() {
        if (BuildConfig.LITE) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        intent.data = ("package:" + requireContext().packageName).toUri()
        installApkLauncher?.launch(intent)
    }

    private fun openFileType(items: RealmMyLibrary, videoType: String) {
        dismissProgressDialog()
        ResourceOpener.openFileType(requireActivity(), items, videoType, profileDbHandler)
    }

    private fun showResourceList(downloadedResources: List<RealmMyLibrary>) {
        val builderSingle = AlertDialog.Builder(ContextThemeWrapper(requireActivity(), R.style.CustomAlertDialog))
        builderSingle.setTitle(getString(R.string.select_resource_to_open))
        val arrayAdapter: ArrayAdapter<RealmMyLibrary?> = object : ArrayAdapter<RealmMyLibrary?>(
            requireActivity(), android.R.layout.select_dialog_item, downloadedResources
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var view = convertView
                if (view == null) view = LayoutInflater.from(activity)
                    .inflate(android.R.layout.select_dialog_item, parent, false)
                val tv = view as TextView
                val library = getItem(position)
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                    if (library?.isResourceOffline() == true) {
                        R.drawable.ic_eye
                    } else {
                        R.drawable.ic_download
                    }, 0)
                tv.setTextColor(context.getColor(R.color.daynight_textColor))
                tv.text = library?.title
                return tv
            }
        }
        builderSingle.setAdapter(arrayAdapter) { _: DialogInterface?, i: Int ->
            val library = arrayAdapter.getItem(i)
            library?.let { openResource(it) }
        }
        builderSingle.setNegativeButton(R.string.dismiss, null).show()
    }

    fun setOpenResourceButton(downloadedResources: List<RealmMyLibrary>?, btnOpen: Button) {
        if (downloadedResources.isNullOrEmpty()) {
            btnOpen.visibility = View.GONE
        } else {
            btnOpen.visibility = View.VISIBLE
            btnOpen.setOnClickListener {
                if (downloadedResources.size == 1) {
                    openResource(downloadedResources[0])
                } else {
                    showResourceList(downloadedResources)
                }
            }
        }
    }
    fun setResourceButton(resources: List<RealmMyLibrary>?, btnResources: Button) {
        if (resources.isNullOrEmpty()) {
            btnResources.visibility = View.GONE
        } else {
            btnResources.visibility = View.VISIBLE
            btnResources.text = getString(R.string.resources_size, resources.size)
            btnResources.setOnClickListener {
                if (resources.isNotEmpty()) {
                    showDownloadDialog(resources)
                }
            }
        }
    }

    open fun handleBackPressed() {
        NavigationHelper.popBackStack(parentFragmentManager)
    }

    override fun onPause() {
        super.onPause()
        dismissProgressDialog()
    }

    override fun onDestroy() {
        dismissProgressDialog()
        super.onDestroy()
    }
}
