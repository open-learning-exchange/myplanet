package org.ole.planet.myplanet.base

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.core.content.FileProvider
import com.google.gson.JsonObject
import io.realm.RealmResults
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.PermissionActivity.Companion.hasInstallPermission
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.service.UserProfileDbHandler.KEY_RESOURCE_DOWNLOAD
import org.ole.planet.myplanet.service.UserProfileDbHandler.KEY_RESOURCE_OPEN
import org.ole.planet.myplanet.ui.course.AdapterCourses
import org.ole.planet.myplanet.ui.viewer.*
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File

abstract class BaseContainerFragment : BaseResourceFragment() {
    var timesRated: TextView? = null
    var rating: TextView? = null
    var ratingBar: AppCompatRatingBar? = null
    override var profileDbHandler: UserProfileDbHandler? = null
    private val INSTALL_UNKNOWN_SOURCES_REQUEST_CODE = 112
    var hasInstallPermission = hasInstallPermission(MainApplication.context)
    private var currentLibrary: RealmMyLibrary? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileDbHandler = UserProfileDbHandler(requireActivity())
    }

    fun setRatings(`object`: JsonObject?) {
        if (`object` != null) {
            AdapterCourses.showRating(`object`, rating, timesRated, ratingBar)
        }
    }
    fun getUrlsAndStartDownload(
        lib: List<RealmMyLibrary?>, settings: SharedPreferences?, urls: ArrayList<String?>
    ) {
        for (library in lib) {
            val url = Utilities.getUrl(library, settings)
            if (!FileUtils.checkFileExist(url) && !TextUtils.isEmpty(url)) urls.add(url)
        }
        if (urls.isNotEmpty()) startDownload(urls) else Utilities.toast(
            activity, getString(R.string.no_images_to_download)
        )
    }
    fun initRatingView(type: String?, id: String?, title: String?, listener: OnRatingChangeListener?) {
        timesRated = requireView().findViewById(R.id.times_rated)
        rating = requireView().findViewById(R.id.tv_rating)
        ratingBar = requireView().findViewById(R.id.rating_bar)
        ratingBar?.setOnTouchListener { _: View?, e: MotionEvent ->
            if (e.action == MotionEvent.ACTION_UP) homeItemClickListener?.showRatingDialog(
                type, id, title, listener
            )
            true
        }
    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
    }
    fun openIntent(items: RealmMyLibrary, typeClass: Class<*>?) {
        val fileOpenIntent = Intent(activity, typeClass)
        if (items.resourceLocalAddress!!.contains("ole/audio") || items.resourceLocalAddress!!.contains("ole/video")) {
            fileOpenIntent.putExtra("TOUCHED_FILE", items.resourceLocalAddress)
        } else {
            fileOpenIntent.putExtra("TOUCHED_FILE", items.id + "/" + items.resourceLocalAddress)
        }
        startActivity(fileOpenIntent)
    }
    fun openPdf(item: RealmMyLibrary) {
        val fileOpenIntent = Intent(activity, PDFReaderActivity::class.java)
        fileOpenIntent.putExtra("TOUCHED_FILE", item.id + "/" + item.resourceLocalAddress)
        fileOpenIntent.putExtra("resourceId", item.id)
        startActivity(fileOpenIntent)
    }
    fun openResource(items: RealmMyLibrary) {
        if (items.resourceOffline != null && items.isResourceOffline()) {
            openFileType(items, "offline")
        } else if (FileUtils.getFileExtension(items.resourceLocalAddress) == "mp4") {
            openFileType(items, "online")
        } else {
            val arrayList = ArrayList<String>()
            arrayList.add(Utilities.getUrl(items, settings))
            startDownload(arrayList)
            profileDbHandler?.setResourceOpenCount(items, KEY_RESOURCE_DOWNLOAD)
        }
    }
    fun checkFileExtension(items: RealmMyLibrary) {
        val filenameArray = items.resourceLocalAddress!!.split("\\.".toRegex()).toTypedArray()
        val extension = filenameArray[filenameArray.size - 1]
        val mimetype = Utilities.getMimeType(items.resourceLocalAddress)
        if (mimetype.contains("image")) {
            openIntent(items, ImageViewerActivity::class.java)
        } else if (mimetype.contains("pdf")) {
            openPdf(items)
        } else if (mimetype.contains("audio")) {
            openIntent(items, AudioPlayerActivity::class.java)
        } else {
            checkMoreFileExtensions(extension, items)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun checkMoreFileExtensions(extension: String?, items: RealmMyLibrary) {
        when (extension) {
            "txt" -> openIntent(items, TextFileViewerActivity::class.java)
            "md" -> openIntent(items, MarkdownViewerActivity::class.java)
            "csv" -> openIntent(items, CSVViewerActivity::class.java)
            "apk" -> installApk(items)
            else -> Toast.makeText(
                activity, getString(R.string.this_file_type_is_currently_unsupported), Toast.LENGTH_LONG
            ).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun installApk(items: RealmMyLibrary) {
        currentLibrary = items
        val directory = File(MainApplication.context.getExternalFilesDir(null).toString() + "/ole" + "/" + items.id)
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw RuntimeException("Failed to create directory: " + directory.absolutePath)
            }
        }

        val apkFile = File(directory, items.resourceLocalAddress)
        if (!apkFile.exists()) {
            Utilities.toast(activity,"APK file not found")
            return
        }

        val uri = FileProvider.getUriForFile(
            MainApplication.context, "${MainApplication.context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = uri
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            if (hasInstallPermission(MainApplication.context)) {
                startActivity(intent)
            } else {
                requestInstallPermission()
            }
        } else {
            Utilities.toast(activity,"No app to handle the installation")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        intent.data = Uri.parse("package:" + MainApplication.context.packageName)
        startActivityForResult(intent, INSTALL_UNKNOWN_SOURCES_REQUEST_CODE)
    }

    fun openFileType(items: RealmMyLibrary, videotype: String) {
        val mimetype = Utilities.getMimeType(items.resourceLocalAddress)
        Utilities.log("Mime type $mimetype")
        Utilities.log("Mime type " + items.resourceLocalAddress)
        if (mimetype == null) {
            Utilities.toast(activity, getString(R.string.unable_to_open_resource))
            return
        }
        if (profileDbHandler == null) profileDbHandler = UserProfileDbHandler(activity)
        profileDbHandler!!.setResourceOpenCount(items, KEY_RESOURCE_OPEN)
        if (mimetype.startsWith("video")) {
            playVideo(videotype, items)
        } else {
            checkFileExtension(items)
        }
    }
    open fun playVideo(videoType: String, items: RealmMyLibrary) {
        val intent = Intent(activity, VideoPlayerActivity::class.java)
        val bundle = Bundle()
        bundle.putString("videoType", videoType)
        if (videoType == "online") {
            bundle.putString("videoURL", "" + Utilities.getUrl(items, settings))
            Log.e("AUTH", "" + auth)
            bundle.putString("Auth", "" + auth)
        } else if (videoType == "offline") {
            if (items.resourceRemoteAddress == null && items.resourceLocalAddress != null) {
                bundle.putString("videoURL", items.resourceLocalAddress)
            } else {
                bundle.putString("videoURL", "" + Uri.fromFile(File("" + FileUtils.getSDPathFromUrl(items.resourceRemoteAddress))))
            }
            bundle.putString("Auth", "")
        }
        intent.putExtras(bundle)
        startActivity(intent)
    }
    private fun showResourceList(downloadedResources: List<RealmMyLibrary>?) {
        val builderSingle = AlertDialog.Builder(requireActivity())
        builderSingle.setTitle(getString(R.string.select_resource_to_open))
        val arrayAdapter: ArrayAdapter<RealmMyLibrary?> = object : ArrayAdapter<RealmMyLibrary?>(
            requireActivity(), android.R.layout.select_dialog_item, downloadedResources!!
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertView = convertView
                if (convertView == null) convertView = LayoutInflater.from(activity)
                    .inflate(android.R.layout.select_dialog_item, parent, false)
                val tv = convertView as TextView
                val library = getItem(position)
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, if (library!!.isResourceOffline()) R.drawable.ic_eye else R.drawable.ic_download, 0)
                tv.text = library.title
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
    fun setResourceButton(resources: RealmResults<*>?, btnResources: Button) {
        if (resources == null || resources.size == 0) {
            btnResources.visibility = View.GONE
        } else {
            btnResources.visibility = View.VISIBLE
            btnResources.text = getString(R.string.resources) + " [" + resources.size + "]"
            btnResources.setOnClickListener {
                if (resources.size > 0) showDownloadDialog(
                    resources as List<RealmMyLibrary>
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == INSTALL_UNKNOWN_SOURCES_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (currentLibrary != null) {
                    installApk(currentLibrary!!)
                    currentLibrary = null
                }
            } else {
                Utilities.toast(requireActivity(), getString(R.string.permissions_denied))
            }
        }
    }

    open fun handleBackPressed() {
        requireActivity().onBackPressed()
    }
}
