package org.ole.planet.takeout.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.lightcouch.Attachment;
import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.utils.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmObject;
import io.realm.RealmResults;
import okhttp3.internal.Util;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.takeout.DashboardFragment.PREFS_NAME;

public class LibraryDatamanager {
    Realm mRealm;
    CouchDbProperties properties;
    Context context;
    public static final String SD_PATH = Environment.getExternalStorageDirectory() + "/ole";
    private static SharedPreferences settings;

    public LibraryDatamanager(Context context, SharedPreferences settings) {
        this.context = context;
        LibraryDatamanager.settings = settings;
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();
        Realm.setDefaultConfiguration(config);
        mRealm = Realm.getInstance(config);
    }

    public List<realm_myLibrary> getLibraryList() {
        return mRealm.where(realm_myLibrary.class).findAll();
    }

    public static String getAttachmentUrl(realm_myLibrary lib) {
        JsonObject jsonObject = new Gson().fromJson(lib.get_attachments(), JsonObject.class);
        if (jsonObject != null) {
            for (String key : jsonObject.keySet()) {
                return getUrl(lib.getResourceId(), key);
            }
        }
        return "";
    }

    public List<realm_myLibrary> getNotDownloadedLibraryList() {
        List<realm_myLibrary> list = getLibraryList();
        List<realm_myLibrary> filteredList = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            final realm_myLibrary lib = list.get(i);
            JsonObject jsonObject = new Gson().fromJson(lib.get_attachments(), JsonObject.class);
            if (jsonObject != null) {
                for (String key : jsonObject.keySet()) {
                    lib.setUrl(getUrl(lib.getResourceId(), key));
                    Utilities.log("URL " + lib.getUrl());
                    if (!checkFileExist(key)) {
                        filteredList.add(lib);
                    }
                }
            }
        }
        return list;
    }


    public static File getSDPathFromUrl(String fileName) {
        return createFilePath(SD_PATH, fileName);
    }

    public static boolean checkFileExist(String fileName) {
        if (fileName == null || fileName.isEmpty())
            return false;
        File f = createFilePath(SD_PATH, fileName);
        return f.exists();

    }

    private static File createFilePath(String folder, String filename) {
        File f = new File(folder);
        if (!f.exists())
            f.mkdirs();
        return new File(f, filename);
    }

    public static String getUrl(String id, String key) {
        return settings.getString("url_Scheme", "") + "://" +
                settings.getString("url_Host", "") + ":" +
                settings.getInt("url_Port", 0)
                + "/resources/" + id + "/" + key
                ;
    }
}
