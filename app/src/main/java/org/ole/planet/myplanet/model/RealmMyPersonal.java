package org.ole.planet.myplanet.model;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.FileUtils;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMyPersonal extends RealmObject {
    @PrimaryKey
    private String _id;
    private boolean uploaded;
    private String title;
    private String description;
    private long date;
    private String userId;
    private String path;


    public static JsonObject serialize(RealmMyPersonal personal) {
        JsonObject object = new JsonObject();
        object.addProperty("title", personal.getTitle());
        object.addProperty("uploadDate", new Date().getTime());
        object.addProperty("createdDate", personal.getDate());
        object.addProperty("filename", FileUtils.getFileNameFromUrl(personal.getPath()));
        object.addProperty("author", personal.getUserId());
        object.addProperty("addedBy", personal.getUserId());
        object.addProperty("description", personal.getDescription());
        object.addProperty("resourceType", "private");
        object.addProperty("mediaType", FileUtils.getMediaType(personal.getPath()));
        return object;
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

