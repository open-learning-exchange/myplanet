package org.ole.planet.myplanet;

import android.content.SharedPreferences;

import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

public class DownloadFiles {

    public static ArrayList downloadAllFiles(List<realm_myLibrary> db_myLibrary, SharedPreferences settings) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < db_myLibrary.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(i), settings));
        }
        return urls;
    }

    public static ArrayList downloadFiles(List<realm_myLibrary> db_myLibrary, ArrayList<Integer> selectedItems, SharedPreferences settings) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < selectedItems.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(selectedItems.get(i)), settings));
        }
        return urls;
    }
}
