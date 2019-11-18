package org.ole.planet.myplanet.model;

import android.content.Context;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.NetworkUtils;

import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.PrimaryKey;

public class RealmApkLog extends RealmObject {
    @Ignore
    public static final String ERROR_TYPE_EXCEPTION = "exception";
    @Ignore
    public static final String ERROR_TYPE_CRASH = "crash";
    @Ignore
    public static final String ERROR_TYPE_ANR = "AnR";
    @PrimaryKey
    private String id;
    private String type;
    private String _rev;
    private String error;
    private String page;
    private String parentCode;
    private String version;
    private String createdOn;
    private String time;
    public String getId() {
        return id;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }


    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getError() {
        return error;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public static JsonObject serialize(RealmApkLog log, Context context) {
        JsonObject object = new JsonObject();
        object.addProperty("type", log.getType());
        object.addProperty("error", log.getError());
        object.addProperty("page", log.getPage());
        object.addProperty("time", log.getTime());
        object.addProperty("version", log.getVersion());
        object.addProperty("createdOn", log.getCreatedOn());
        object.addProperty("androidId", log.getCreatedOn());
        object.addProperty("createdOn", log.getCreatedOn());
        object.addProperty("androidId", NetworkUtils.getMacAddr());
        object.addProperty("deviceName", NetworkUtils.getDeviceName());
        object.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context));
        object.addProperty("parentCode", log.getParentCode());
        return object;
    }

    public void setError(Throwable e) {

        this.error += "--------- Stack trace ---------\n\n";
        appendReport(e);
        this.error += "--------- Cause ---------\n\n";
        Throwable cause = e.getCause();
        appendReport(cause);
    }

    private void appendReport(Throwable cause) {
        if (cause != null) {
            this.error += cause.toString() + "\n\n";
            StackTraceElement[] arr = cause.getStackTrace();
            for (int i = 0; i < arr.length; i++) {
                this.error += "    " + arr[i].toString() + "\n";
            }
        }
        this.error += "-------------------------------\n\n";
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }
}
