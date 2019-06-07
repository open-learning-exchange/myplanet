package org.ole.planet.myplanet.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMessage extends RealmObject {
    @PrimaryKey
    String id;
    private String message;

    private String time;

    private String user;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public static JsonElement serialize(RealmList<RealmMessage> messages) {
        JsonArray array = new JsonArray();
        for (RealmMessage ms : messages) {
            JsonObject object = new JsonObject();
            object.addProperty("user",ms.getUser());
            object.addProperty("time",ms.getTime());
            object.addProperty("message",ms.getMessage());
            array.add(object);
        }
        return array;
    }

      public static void insertFeedback(Realm mRealm, JsonObject act) {

      }
}
