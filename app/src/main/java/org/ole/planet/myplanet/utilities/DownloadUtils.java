package org.ole.planet.myplanet.utilities;

import android.content.SharedPreferences;

import org.ole.planet.myplanet.model.RealmMyLibrary;

import java.util.ArrayList;
import java.util.List;

public class DownloadUtils {

    public static ArrayList downloadAllFiles(List<RealmMyLibrary> db_myLibrary, SharedPreferences settings) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < db_myLibrary.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(i), settings));
        }
        return urls;
    }

    public static ArrayList downloadFiles(List<RealmMyLibrary> db_myLibrary, ArrayList<Integer> selectedItems, SharedPreferences settings) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < selectedItems.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(selectedItems.get(i)), settings));
        }
        return urls;
    }
}
