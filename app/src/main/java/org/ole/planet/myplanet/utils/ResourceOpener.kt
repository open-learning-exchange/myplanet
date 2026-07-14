package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.Intent
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.ui.viewer.ResourceViewerActivity
import org.ole.planet.myplanet.ui.viewer.ResourceViewerFragment

object ResourceOpener {
    private fun resourcePath(item: RealmMyLibrary): String {
        return "${item.id}/${item.resourceLocalAddress}"
    }

    private fun resolveType(item: RealmMyLibrary): ResourceViewerFragment.ResourceType {
        val mimetype = Utilities.getMimeType(item.resourceLocalAddress)
        val extension = item.resourceLocalAddress?.substringAfterLast(".", "")
        return when {
            mimetype?.contains("video") == true -> ResourceViewerFragment.ResourceType.VIDEO
            mimetype?.contains("audio") == true -> ResourceViewerFragment.ResourceType.AUDIO
            mimetype?.contains("pdf") == true -> ResourceViewerFragment.ResourceType.PDF
            mimetype?.contains("image") == true -> ResourceViewerFragment.ResourceType.IMAGE
            extension == "txt" -> ResourceViewerFragment.ResourceType.TEXT
            extension == "md" -> ResourceViewerFragment.ResourceType.MARKDOWN
            extension == "csv" -> ResourceViewerFragment.ResourceType.CSV
            else -> ResourceViewerFragment.ResourceType.UNKNOWN
        }
    }

    private fun openResource(activity: Activity, item: RealmMyLibrary, isOnline: Boolean, type: ResourceViewerFragment.ResourceType? = null) {
        val resolvedType = type ?: resolveType(item)
        val filePath = if (isOnline) {
            UrlUtils.getUrl(item)
        } else {
            if (item.resourceLocalAddress?.contains("ole/audio") == true ||
                item.resourceLocalAddress?.contains("ole/video") == true) {
                item.resourceLocalAddress
            } else {
                resourcePath(item)
            }
        }

        val intent = Intent(activity, ResourceViewerActivity::class.java).apply {
            putExtra("TOUCHED_FILE", filePath)
            putExtra("RESOURCE_TITLE", item.title)
            putExtra("resourceId", item.id)
            putExtra("isOnline", isOnline)
            putExtra("resourceType", resolvedType.name)
        }
        activity.startActivity(intent)
    }

    fun getResourceTypeIcon(localAddress: String?): Int {
        val mimeType = Utilities.getMimeType(localAddress)
        return when {
            mimeType == null -> R.drawable.ic_folder
            mimeType.startsWith("video") -> R.drawable.ic_play
            mimeType.startsWith("image") -> R.drawable.ic_camera
            mimeType.startsWith("audio") -> R.drawable.ic_mic
            mimeType.contains("pdf") -> R.drawable.ic_folder
            else -> R.drawable.ic_folder
        }
    }

    fun openFileType(activity: Activity, items: RealmMyLibrary, videoType: String, profileDbHandler: UserSessionManager) {
        val mimetype = Utilities.getMimeType(resourcePath(items))
        if (mimetype == null) {
            Utilities.toast(activity, activity.getString(R.string.unable_to_open_resource))
            return
        }
        profileDbHandler.setResourceOpenCount(items, UserSessionManager.KEY_RESOURCE_OPEN)
        openResource(activity, items, videoType == "online")
    }
}
