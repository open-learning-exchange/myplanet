package org.ole.planet.myplanet.utilities

import org.ole.planet.myplanet.model.RealmMyLibrary

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
}
