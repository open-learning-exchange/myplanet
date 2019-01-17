package org.ole.planet.myplanet.model;

import com.google.gson.JsonObject;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmApkLog extends RealmObject {
    public static final String ERROR_TYPE_EXCEPTION = "exception";
    public static final String ERROR_TYPE_CRASH = "crash";
    public static final String ERROR_TYPE_ANR = "AnR";
    @PrimaryKey
    private String id;
    private String type;
    private String _rev;
    private String error;
    private String page;

    public String getId() {
        return id;
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


    public static JsonObject serialize(RealmApkLog log) {
        JsonObject object = new JsonObject();
        object.addProperty("type", log.getType());
        object.addProperty("error", log.getError());
        object.addProperty("page", log.getPage());
        return object;
    }

    public void setError(Throwable e) {
        StackTraceElement[] arr = e.getStackTrace();
        String report = e.toString() + "\n\n";
        report += "--------- Stack trace ---------\n\n";
        for (int i = 0; i < arr.length; i++) {
            report += "    " + arr[i].toString() + "\n";
        }
        report += "-------------------------------\n\n";
        report += "--------- Cause ---------\n\n";
        Throwable cause = e.getCause();
        if (cause != null) {
            report += cause.toString() + "\n\n";
            arr = cause.getStackTrace();
            for (int i = 0; i < arr.length; i++) {
                report += "    " + arr[i].toString() + "\n";
            }
        }
        report += "-------------------------------\n\n";
        this.error = report;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }
}
