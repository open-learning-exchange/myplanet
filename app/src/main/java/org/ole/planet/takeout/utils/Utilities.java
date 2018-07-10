package org.ole.planet.takeout.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import static org.ole.planet.takeout.library.LibraryDatamanager.SD_PATH;

public class Utilities {

    public static void log(String message){
        Log.d("OLE ", "log: " + message);
    }

    private static String getFileNameFromUrl(String url) {
        try {
            return url.substring(url.lastIndexOf("/") + 1);
        } catch (Exception e) {
        }
        return "";

    }

    public static File getSDPathFromUrl(String url) {
        return createFilePath(SD_PATH, getFileNameFromUrl(url));
    }

    public static boolean checkFileExist(String url) {
        if (url == null || url.isEmpty())
            return false;
        File f = createFilePath(SD_PATH, getFileNameFromUrl(url));
        return f.exists();

    }

    private static File createFilePath(String folder, String filename) {
        File f = new File(folder);
        if (!f.exists())
            f.mkdirs();
        Utilities.log("Return file "+ folder + "/" + filename);
        return new File(f, filename);
    }

    public static void toast(Context context, String s) {
        if (context == null){
            return;
        }
        Toast.makeText(context,s, Toast.LENGTH_LONG ).show();
    }
}
