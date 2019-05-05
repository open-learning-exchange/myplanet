package org.ole.planet.myplanet.utilities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.MyDownloadService;
import org.ole.planet.myplanet.model.RealmMyLibrary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import fisk.chipcloud.ChipCloudConfig;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.myplanet.ui.sync.SyncActivity.PREFS_NAME;


public class Utilities {
    public static final String SD_PATH = Environment.getExternalStorageDirectory() + "/ole";

    public static void log(String message) {
        Log.d("OLE ", "log: " + message);
    }


    public static String getUrl(RealmMyLibrary library, SharedPreferences settings) {
        return getUrl(library.getResource_id(), library.getResourceLocalAddress(), settings);

    }

    public static String getUrl(String id, String file, SharedPreferences settings) {
        return getUrl()
                + "/resources/" + id + "/" + file;
    }

    private static String getServerUrl(SharedPreferences settings) {
        return settings.getString("url_Scheme", "") + "://" +
                settings.getString("url_Host", "") + ":" +
                settings.getInt("url_Port", 0) + "/";
    }


    public static String getUserImageUrl(String userId, String imageName, SharedPreferences settings) {
        return getUrl() + "/_users/" + userId + "/" + imageName;
    }

    public static String currentDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dateformat = new SimpleDateFormat("EEE dd, MMMM yyyy");
        String datetime = dateformat.format(c.getTime());
        return datetime;
    }

    public static String formatDate(long date) {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dateformat = new SimpleDateFormat("EEE dd, MMMM yyyy");
        String datetime = dateformat.format(date);
        return datetime;
    }


    public static void openDownloadService(Context context, ArrayList urls) {
        Intent intent = new Intent(context, MyDownloadService.class);
        intent.putStringArrayListExtra("urls", urls);
        context.startService(intent);
    }


    public static void toast(Context context, String s) {
        if (context == null) {
            return;
        }
        Toast.makeText(context, s, Toast.LENGTH_LONG).show();
    }

    public static ChipCloudConfig getCloudConfig() {
        return new ChipCloudConfig()
                .useInsetPadding(true)
                .checkedChipColor(Color.parseColor("#e0e0e0"))
                .checkedTextColor(Color.parseColor("#000000"))
                .uncheckedChipColor(Color.parseColor("#e0e0e0"))
                .uncheckedTextColor(Color.parseColor("#000000"));
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

    public static String getUserName(SharedPreferences settings) {
        return settings.getString("name", "");
    }

    public static void loadImage(String userImage, ImageView imageView) {
        if (!TextUtils.isEmpty(userImage)) {
            Picasso.get().load(userImage).placeholder(R.drawable.profile).error(R.drawable.profile).into(imageView);
        }
    }

    public static void handleCheck(boolean b, int i, List<Object> selectedItems, List<?> list) {
        if (b) {
            selectedItems.add(list.get(i));
        } else if (selectedItems.contains(list.get(i))) {
            selectedItems.remove(list.get(i));
        }

    }

    public static String getHeader() {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return "Basic " + Base64.encodeToString((settings.getString("url_user", "") + ":" +
                settings.getString("url_pwd", "")).getBytes(), Base64.NO_WRAP);
    }

    public static String getUrl() {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (settings.contains("couchdbURL")) {
            String url = settings.getString("couchdbURL", "");

            if (!url.endsWith("/db")) {
                url += "/db";
            }
            return url;
        }
        return "";
    }

    public static String getUpdateUrl(SharedPreferences settings) {
        String url = settings.getString("couchdbURL", "");
        if (url.endsWith("/db")) {
            url.replace("/db", "");
        }
        return url + "/versions";
    }

    public static String getApkUpdateUrl(String path) {
        SharedPreferences preferences = MainApplication.context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String url = preferences.getString("couchdbURL", "");
        if (url.endsWith("/db")) {
            url.replace("/db", "");
        }
        return url + path;
    }

}