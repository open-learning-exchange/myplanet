package org.ole.planet.myplanet.utilities

import java.util.regex.Pattern
import kotlin.text.isNotEmpty
import io.realm.Realm
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.FileUtils

object DownloadUtils {
    @JvmStatic
    fun downloadAllFiles(dbMyLibrary: List<RealmMyLibrary?>): ArrayList<String> {
        val urls = ArrayList<String>()
        for (i in dbMyLibrary.indices) {
            urls.add(Utilities.getUrl(dbMyLibrary[i]))
        }
        return urls
    }

    @JvmStatic
    fun downloadFiles(dbMyLibrary: List<RealmMyLibrary?>, selectedItems: ArrayList<Int>): ArrayList<String> {
        val urls = ArrayList<String>()
        for (i in selectedItems.indices) {
            urls.add(Utilities.getUrl(dbMyLibrary[selectedItems[i]]))
        }
        return urls
    }

    fun extractLinks(text: String?): ArrayList<String> {
        val links = ArrayList<String>()
        val pattern = Pattern.compile("!\\[.*?]\\((.*?)\\)")
        val matcher = text?.let { pattern.matcher(it) }
        if (matcher != null) {
            while (matcher.find()) {
                val link = matcher.group(1)
                if (link != null) {
                    if (link.isNotEmpty()) {
                        links.add(link)
                    }
                }
            }
        }
        return links
    }

    @JvmStatic
    fun updateResourceOfflineStatus(url: String) {
        val currentFileName = FileUtils.getFileNameFromUrl(url)
        try {
            val backgroundRealm = Realm.getDefaultInstance()
            backgroundRealm.use { realm ->
                realm.executeTransaction {
                    realm.where(RealmMyLibrary::class.java)
                        .equalTo("resourceLocalAddress", currentFileName)
                        .findAll()?.forEach {
                            it.resourceOffline = true
                            it.downloadedRev = it._rev
                        }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
