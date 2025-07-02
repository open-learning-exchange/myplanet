package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.viewer.*
import java.io.File

object ResourceOpener {
    fun openIntent(activity: Activity, items: RealmMyLibrary, typeClass: Class<*>) {
        val fileOpenIntent = Intent(activity, typeClass)
        if (items.resourceLocalAddress?.contains("ole/audio") == true ||
            items.resourceLocalAddress?.contains("ole/video") == true) {
            fileOpenIntent.putExtra("TOUCHED_FILE", items.resourceLocalAddress)
            fileOpenIntent.putExtra("RESOURCE_TITLE", items.title)
        } else {
            fileOpenIntent.putExtra("TOUCHED_FILE", items.id + "/" + items.resourceLocalAddress)
            fileOpenIntent.putExtra("RESOURCE_TITLE", items.title)
        }
        activity.startActivity(fileOpenIntent)
    }

    fun openPdf(activity: Activity, item: RealmMyLibrary) {
        val fileOpenIntent = Intent(activity, PDFReaderActivity::class.java)
        fileOpenIntent.putExtra("TOUCHED_FILE", item.id + "/" + item.resourceLocalAddress)
        fileOpenIntent.putExtra("resourceId", item.id)
        activity.startActivity(fileOpenIntent)
    }

    private fun checkMoreFileExtensions(activity: Activity, extension: String?, items: RealmMyLibrary) {
        when (extension) {
            "txt" -> openIntent(activity, items, TextFileViewerActivity::class.java)
            "md" -> openIntent(activity, items, MarkdownViewerActivity::class.java)
            "csv" -> openIntent(activity, items, CSVViewerActivity::class.java)
            else -> Toast.makeText(activity,
                activity.getString(R.string.this_file_type_is_currently_unsupported),
                Toast.LENGTH_LONG).show()
        }
    }

    fun checkFileExtension(activity: Activity, items: RealmMyLibrary) {
        val filenameArray = items.resourceLocalAddress?.split(".")?.toTypedArray()
        val extension = filenameArray?.get(filenameArray.size - 1)
        val mimetype = Utilities.getMimeType(items.resourceLocalAddress)

        if (mimetype != null) {
            when {
                mimetype.contains("image") -> openIntent(activity, items, ImageViewerActivity::class.java)
                mimetype.contains("pdf") -> openPdf(activity, items)
                mimetype.contains("audio") -> openIntent(activity, items, AudioPlayerActivity::class.java)
                else -> checkMoreFileExtensions(activity, extension, items)
            }
        }
    }

    fun playVideo(activity: Activity, videoType: String, items: RealmMyLibrary) {
        val intent = Intent(activity, VideoPlayerActivity::class.java)
        val bundle = Bundle()
        bundle.putString("videoType", videoType)
        if (videoType == "online") {
            bundle.putString("videoURL", "" + Utilities.getUrl(items))
            bundle.putString("Auth", "" + BaseResourceFragment.auth)
        } else if (videoType == "offline") {
            if (items.resourceRemoteAddress == null && items.resourceLocalAddress != null) {
                bundle.putString("videoURL", items.resourceLocalAddress)
            } else {
                bundle.putString(
                    "videoURL",
                    "" + Uri.fromFile(File("" + FileUtils.getSDPathFromUrl(items.resourceRemoteAddress)))
                )
            }
            bundle.putString("Auth", "")
        }
        intent.putExtras(bundle)
        activity.startActivity(intent)
    }

    fun openFileType(activity: Activity, items: RealmMyLibrary, videoType: String, profileDbHandler: UserProfileDbHandler) {
        val mimetype = Utilities.getMimeType(items.resourceLocalAddress)
        if (mimetype == null) {
            Utilities.toast(activity, activity.getString(R.string.unable_to_open_resource))
            return
        }
        profileDbHandler.setResourceOpenCount(items, UserProfileDbHandler.KEY_RESOURCE_OPEN)
        if (mimetype.startsWith("video")) {
            playVideo(activity, videoType, items)
        } else {
            checkFileExtension(activity, items)
        }
    }
}

