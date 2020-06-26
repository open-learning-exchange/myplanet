package org.ole.planet.myplanet.model;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMyPersonal extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private boolean uploaded;
    private String title;
    private String description;
    private long date;
    private String userId;
    private String userName;
    private String path;


    public static JsonObject serialize(RealmMyPersonal personal, Context context) {
        JsonObject object = new JsonObject();
        object.addProperty("title", personal.getTitle());
        object.addProperty("uploadDate", new Date().getTime());
        object.addProperty("createdDate", personal.getDate());
        object.addProperty("filename", FileUtils.getFileNameFromUrl(personal.getPath()));
        object.addProperty("author", personal.getUserName());
        object.addProperty("addedBy", personal.getUserName());
        object.addProperty("description", personal.getDescription());
        object.addProperty("resourceType", "Activities");
        object.addProperty("private", true);
        JsonObject object1 = new JsonObject();
        object.addProperty("androidId", NetworkUtils.getMacAddr());
        object.addProperty("deviceName", NetworkUtils.getDeviceName());
        object.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context));
        object1.addProperty("users", personal.getUserId());
        object.add("privateFor", object1);
//        object.addProperty("mediaType", FileUtils.getMediaType(personal.getPath()));
        return object;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String get_rev() {
        return _rev;
    }

    public String getTitle() {
        return title;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}

