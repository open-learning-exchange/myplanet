package org.ole.planet.myplanet.Data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Date;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_messages extends RealmObject {
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

    public static JsonElement serialize(RealmList<realm_messages> messages) {
        JsonArray array = new JsonArray();
        for (realm_messages ms : messages) {
            JsonObject object = new JsonObject();
            object.addProperty("user",ms.getUser());
            object.addProperty("time",ms.getTime());
            object.addProperty("message",ms.getMessage());
            array.add(object);
        }
        return array;
    }
}
