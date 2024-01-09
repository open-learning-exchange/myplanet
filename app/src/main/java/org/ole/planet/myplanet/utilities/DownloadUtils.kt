package org.ole.planet.myplanet.utilities

import android.content.SharedPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary

object DownloadUtils {
    @JvmStatic
    fun downloadAllFiles(db_myLibrary: List<RealmMyLibrary?>, settings: SharedPreferences?): ArrayList<String> {
        val urls = ArrayList<String>()
        for (i in db_myLibrary.indices) {
            urls.add(Utilities.getUrl(db_myLibrary[i], settings))
        }
        return urls
    }

    @JvmStatic
    fun downloadFiles(db_myLibrary: List<RealmMyLibrary?>, selectedItems: ArrayList<Int>, settings: SharedPreferences?): ArrayList<String> {
        val urls = ArrayList<String>()
        for (i in selectedItems.indices) {
            urls.add(Utilities.getUrl(db_myLibrary[selectedItems[i]!!], settings))
        }
        return urls
    }
}