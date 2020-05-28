package org.ole.planet.myplanet.model;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import androidx.annotation.RequiresApi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.myplanet.ui.sync.SyncActivity.PREFS_NAME;

public class MyPlanet implements Serializable {
    private String planetVersion;

    private String latestapk;

    private String minapk;

    private int minapkcode;

    private int latestapkcode;

    private String apkpath;

    private String appname;

    private String localapkpath;

    public String getPlanetVersion() {
        return planetVersion;
    }

    public void setPlanetVersion(String planetVersion) {
        this.planetVersion = planetVersion;
    }

    public String getLatestapk() {
        return latestapk;
    }

    public void setLatestapk(String latestapk) {
        this.latestapk = latestapk;
    }

    public String getMinapk() {
        return minapk;
    }

    public void setMinapk(String minapk) {
        this.minapk = minapk;
    }

    public String getApkpath() {
        return apkpath;
    }

    public void setApkpath(String apkpath) {
        this.apkpath = apkpath;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public int getMinapkcode() {
        return minapkcode;
    }

    public void setMinapkcode(int minapkcode) {
        this.minapkcode = minapkcode;
    }

    public int getLatestapkcode() {
        return latestapkcode;
    }

    public void setLatestapkcode(int latestapkcode) {
        this.latestapkcode = latestapkcode;
    }

    public String getLocalapkpath() {
        return localapkpath;
    }

    public void setLocalapkpath(String localapkpath) {
        this.localapkpath = localapkpath;
    }

    @Override
    public String toString() {
        return appname;
    }

    public static JsonObject getMyPlanetActivities(Context context, SharedPreferences pref, RealmUserModel model) {
        JsonObject postJSON = new JsonObject();
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        MyPlanet planet = new Gson().fromJson(preferences.getString("versionDetail", ""), MyPlanet.class);
        if (planet != null)
            postJSON.addProperty("planetVersion", planet.getPlanetVersion());
        postJSON.addProperty("_id", VersionUtils.getAndroidId(MainApplication.context) + "@" + NetworkUtils.getMacAddr());
        postJSON.addProperty("last_synced", pref.getLong("LastSync", 0));
        postJSON.addProperty("parentCode", model.getParentCode());
        postJSON.addProperty("createdOn", model.getPlanetCode());
        postJSON.addProperty("macAddress", NetworkUtils.getMacAddr());
        postJSON.addProperty("type", "usages");
        postJSON.add("usages", getTabletUsages(context, pref));
        return postJSON;
    }


    public static JsonObject getNormalMyPlanetActivities(Context context, SharedPreferences pref, RealmUserModel model) {
        JsonObject postJSON = new JsonObject();
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        MyPlanet planet = new Gson().fromJson(preferences.getString("versionDetail", ""), MyPlanet.class);
        if (planet != null)
            postJSON.addProperty("planetVersion", planet.getPlanetVersion());
        postJSON.addProperty("last_synced", pref.getLong("LastSync", 0));
        postJSON.addProperty("parentCode", model.getParentCode());
        postJSON.addProperty("createdOn", model.getPlanetCode());
        postJSON.addProperty("macAddress", NetworkUtils.getMacAddr());
        postJSON.addProperty("version", VersionUtils.getVersionCode(context));
        postJSON.addProperty("versionName", VersionUtils.getVersionName(context));
        postJSON.addProperty("androidId", NetworkUtils.getMacAddr());
        postJSON.addProperty("uniqueAndroidId", VersionUtils.getAndroidId(MainApplication.context));
        postJSON.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context));
        postJSON.addProperty("deviceName", NetworkUtils.getDeviceName());
        postJSON.addProperty("time", new Date().getTime());
        postJSON.addProperty("type", "sync");
        JsonObject gps = new JsonObject();
        gps.addProperty("latitude", pref.getString("last_lat", ""));
        gps.addProperty("longitude", pref.getString("last_lng", ""));
        postJSON.add("gps", gps);
        return postJSON;
    }

    public static JsonArray getTabletUsages(Context context, SharedPreferences pref) {
        Calendar cal = Calendar.getInstance();
        SharedPreferences settings = MainApplication.context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        cal.setTimeInMillis(settings.getLong("lastUsageUploaded", 0));
        JsonArray arr = new JsonArray();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) MainApplication.context.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> queryUsageStats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.getTimeInMillis(),
                    System.currentTimeMillis());
            for (UsageStats s : queryUsageStats) {
                addStats(s, arr, context, pref);
            }
        }
        return arr;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static void addStats(UsageStats s, JsonArray arr, Context context, SharedPreferences pref) {
        if (s.getPackageName().equals(MainApplication.context.getPackageName())) {
            JsonObject object = new JsonObject();
            object.addProperty("lastTimeUsed", s.getLastTimeUsed() > 0 ? s.getLastTimeUsed() : 0);
            object.addProperty("firstTimeUsed", s.getFirstTimeStamp() > 0 ? s.getLastTimeStamp() :0);
            object.addProperty("totalForegroundTime", s.getTotalTimeInForeground());
            long totalUsed = s.getLastTimeUsed() - s.getFirstTimeStamp();
            object.addProperty("totalUsed", totalUsed > 0 ? totalUsed : 0);
            object.addProperty("version", VersionUtils.getVersionCode(context));
            object.addProperty("versionName", VersionUtils.getVersionName(context));
            object.addProperty("androidId", NetworkUtils.getMacAddr());
            object.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context));
            object.addProperty("deviceName", NetworkUtils.getDeviceName());
            object.addProperty("time", new Date().getTime());
            JsonObject gps = new JsonObject();
            gps.addProperty("latitude", pref.getString("last_lat", ""));
            gps.addProperty("longitude", pref.getString("last_lng", ""));
            object.add("gps", gps);
            arr.add(object);
        }
    }
}
