package org.ole.planet.myplanet.model;

import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMyHealthPojo extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private String userId;
    private String _rev;
    private String data;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public static void insert(Realm mRealm, JsonObject act) {
        RealmMyHealthPojo myHealth = mRealm.where(RealmMyHealthPojo.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (myHealth == null)
            myHealth = mRealm.createObject(RealmMyHealthPojo.class, JsonUtils.getString("_id", act));
        myHealth.setData(JsonUtils.getString("data", act));
        myHealth.set_rev(JsonUtils.getString("_rev", act));
        myHealth.set_id(JsonUtils.getString("_id", act));
        myHealth.setUserId(JsonUtils.getString("userId", act));
    }

    public static JsonObject serialize(RealmMyHealthPojo health) {
        JsonObject object = new JsonObject();
        if (!TextUtils.isEmpty(health.get_id())) {
            object.addProperty("_id", health.get_id());
            object.addProperty("_rev", health.get_rev());
        } else {
            object.addProperty("_id", health.getUserId());
        }
        object.addProperty("data", health.getData());
        object.addProperty("userId", health.getUserId());
        return object;
    }
}
