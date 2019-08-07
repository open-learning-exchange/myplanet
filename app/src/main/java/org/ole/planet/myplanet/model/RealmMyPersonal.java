package org.ole.planet.myplanet.model;

import com.google.gson.JsonObject;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMyPersonal extends RealmObject {
    @PrimaryKey
   private String _id;
   private String title;
   private String description;
   private long date;
   private String userId;
   private String path;


   public static JsonObject serialize(RealmMyPersonal personal){
       JsonObject object = new JsonObject();
       object.addProperty("date", personal.getDate());
       object.addProperty("path", personal.getPath());
       object.addProperty("userId", personal.getUserId());
       object.addProperty("description", personal.getDescription());
       return object;
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

