package org.ole.planet.myplanet.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.util.Date;

public class MyPlanet {
    private String planetVersion;

    private String latestapk;

    private String minapk;

    private int minapkcode;

    private int latestapkcode;

    private String apkpath;

    private String appname;

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

    @Override
    public String toString() {
        return "ClassPojo [planetVersion = " + planetVersion + ", latestapk = " + latestapk + ", minapk = " + minapk + ", apkpath = " + apkpath + ", appname = " + appname + "]";
    }

    public static JsonObject getMyPlanetActivities(Context context, SharedPreferences pref, RealmUserModel model){
        JsonObject postJSON = new JsonObject();
        postJSON.addProperty("last_synced", pref.getLong("LastSync", 0));
        postJSON.addProperty("version", VersionUtils.getVersionCode(context));
        postJSON.addProperty("versionName", VersionUtils.getVersionName(context));
        postJSON.addProperty("parentCode", model.getParentCode());
        postJSON.addProperty("createdOn", model.getPlanetCode());
        postJSON.addProperty("androidId", NetworkUtils.getMacAddr());
        postJSON.addProperty("deviceName", NetworkUtils.getDeviceName());
        postJSON.addProperty("time", new Date().getTime());
        JsonObject gps = new JsonObject();
        gps.addProperty("latitude", pref.getString("last_lat", ""));
        gps.addProperty("longitude", pref.getString("last_lng", ""));
        postJSON.add("gps", gps);
        return postJSON;
    }
}
