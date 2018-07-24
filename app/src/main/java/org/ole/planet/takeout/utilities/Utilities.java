package org.ole.planet.takeout.utilities;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.MyDownloadService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;


public class Utilities {
    public static final String SD_PATH = Environment.getExternalStorageDirectory() + "/ole";

    public static void log(String message) {
        Log.d("OLE ", "log: " + message);
    }

    public static String getFileNameFromUrl(String url) {
        try {
            return url.substring(url.lastIndexOf("/") + 1);
        } catch (Exception e) {
        }
        return "";

    }

    public static String getUrl(realm_myLibrary library, SharedPreferences settings) {
        return getUrl(library.getResourceId(), library.getResourceLocalAddress(), settings);

    }

    public static String getUrl(String id, String file, SharedPreferences settings) {
        return getServerUrl(settings)
                + "resources/" + id + "/" + file;
    }

    private static String getServerUrl(SharedPreferences settings) {
        return settings.getString("url_Scheme", "") + "://" +
                settings.getString("url_Host", "") + ":" +
                settings.getInt("url_Port", 0) + "/";
    }


    public static String getUserImageUrl(String userId, String imageName, SharedPreferences settings) {
        return getServerUrl(settings) + "_users/" + userId + "/" + imageName;
    }

    public static String currentDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dateformat = new SimpleDateFormat("dd-MMM-yyyy");
        String datetime = dateformat.format(c.getTime());
        return datetime;
    }

    public static void openDownloadService(Context context, ArrayList urls) {
        Intent intent = new Intent(context, MyDownloadService.class);
        intent.putStringArrayListExtra("urls", urls);
        context.startService(intent);
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
        Utilities.log("Return file " + folder + "/" + filename);
        return new File(f, filename);
    }

    public static void toast(Context context, String s) {
        if (context == null) {
            return;
        }
        Toast.makeText(context, s, Toast.LENGTH_LONG).show();
    }


    public static void showAlert(Context context, String link) {
        File file = Utilities.getSDPathFromUrl(link);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (link.contains("pdf")) {
            intent.setDataAndType(Uri.fromFile(file), "application/pdf");
        } else if (link.contains("mp3")) {
            intent.setDataAndType(Uri.fromFile(file), "audio/*");
        } else if (isImage(link)) {
            intent.setDataAndType(Uri.fromFile(file), "image/*");
        } else if (isVideo(link)) {
            intent.setDataAndType(Uri.fromFile(file), "video*/*");
        }
        openIntent(context, intent);
//        return null;
    }

    private static boolean isVideo(String link) {
        return link.contains(".mp4") || link.contains(".avi");
    }

    private static boolean isImage(String link) {
        return link.contains(".jpg") || link.contains(".jpeg") || link.contains(".png");
    }

    private static void openIntent(Context context, Intent intent) {
        intent.putExtra(Intent.EXTRA_SUBJECT, "Open With ..");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        Intent openInent = Intent.createChooser(intent, context.getString(R.string.app_name));
        try {
            context.startActivity(openInent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No File reader found. please download the reader from playstore", Toast.LENGTH_SHORT).show();
        }
    }

    public static String checkNA(String s) {
        return TextUtils.isEmpty(s) ? "N/A" : s;
    }


    public static String getRelativeTime(long timestamp) {
        long nowtime = System.currentTimeMillis();
        if (timestamp < nowtime) {
            return (String) DateUtils.getRelativeTimeSpanString(timestamp, nowtime, 0);
        }
        return "Just now";
    }

    public static String getFullName(SharedPreferences settings) {
        return settings.getString("firstName", "") + " " + settings.getString("middleName", "") + " " + settings.getString("lastName", "");
    }

    public static void loadImage(String userImage, ImageView imageView) {
        if (!TextUtils.isEmpty(userImage)) {
            Picasso.get().load(userImage).placeholder(R.drawable.profile).error(R.drawable.profile).into(imageView);
        }
    }
}