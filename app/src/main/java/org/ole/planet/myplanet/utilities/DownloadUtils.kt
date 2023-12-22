package org.ole.planet.myplanet.utilities

import android.content.SharedPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary

class DownloadUtils {
    companion object {
        @JvmStatic
        fun downloadAllFiles(dbMyLibrary: List<RealmMyLibrary>, settings: SharedPreferences): ArrayList<String> {
            val urls = ArrayList<String>()
            for (i in dbMyLibrary.indices) {
                urls.add(Utilities.getUrl(dbMyLibrary[i], settings))
            }
            return urls
        }

        @JvmStatic
        fun downloadFiles(dbMyLibrary: List<RealmMyLibrary>, selectedItems: ArrayList<Int>, settings: SharedPreferences): ArrayList<String> {
            val urls = ArrayList<String>()
            for (i in selectedItems) {
                urls.add(Utilities.getUrl(dbMyLibrary[i], settings))
            }
            return urls
        }
    }
}
