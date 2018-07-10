package org.ole.planet.takeout.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import org.ole.planet.takeout.R;

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


    public static void showAlert(Context context, String link) {
        File file = Utilities.getSDPathFromUrl(link);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (link.contains("pdf")) {
            intent.setDataAndType(Uri.fromFile(file), "application/pdf");
        } else if (link.contains("mp3")) {
            intent.setDataAndType(Uri.fromFile(file), "audio/*");
        } else if (link.contains(".jpg") || link.contains(".jpeg") || link.contains(".png")) {
            intent.setDataAndType(Uri.fromFile(file), "image/*");
        } else if (link.contains(".3gp") || link.contains(".mpg") || link.contains(".mpeg") || link.contains(".mpe") || link.contains(".mp4") || link.contains(".avi")) {
            intent.setDataAndType(Uri.fromFile(file), "video*/*");
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, "Download Complete, Open With ..");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        Intent openInent = Intent.createChooser(intent, context.getString(R.string.app_name));
        try {
            context.startActivity(openInent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No File reader found. please download the reader from playstore", Toast.LENGTH_SHORT).show();
        }
//        return null;
    }
}
