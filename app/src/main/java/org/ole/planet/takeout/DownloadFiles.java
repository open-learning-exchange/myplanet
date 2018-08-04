package org.ole.planet.takeout;

import android.content.SharedPreferences;

import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.utilities.Utilities;
import java.util.ArrayList;
import io.realm.RealmResults;

public class DownloadFiles{

    public static ArrayList downloadAllFiles(RealmResults<realm_myLibrary> db_myLibrary, SharedPreferences settings) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < db_myLibrary.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(i), settings));
        }
        return urls;
    }

    public static ArrayList downloadFiles(RealmResults<realm_myLibrary> db_myLibrary, ArrayList<Integer> selectedItems, SharedPreferences settings) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < selectedItems.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(selectedItems.get(i)), settings));
        }
        return urls;
    }
}
