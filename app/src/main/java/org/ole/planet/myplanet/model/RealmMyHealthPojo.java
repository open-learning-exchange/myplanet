package org.ole.planet.myplanet.model;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMyHealthPojo extends RealmObject {
    @PrimaryKey
    private String _id;
    private String _rev;
    private String data;

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
        Utilities.log("Insert myhealth " + act);
        RealmMyHealthPojo myHealth = mRealm.where(RealmMyHealthPojo.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (myHealth == null)
            myHealth = mRealm.createObject(RealmMyHealthPojo.class, JsonUtils.getString("_id", act));
        myHealth.setData(JsonUtils.getString("data", act));
        myHealth.set_rev(JsonUtils.getString("_rev", act));
    }

    public static JsonObject serialize(RealmMyHealthPojo health) {
        JsonObject object = new JsonObject();
        object.addProperty("_id", health.get_id());
        object.addProperty("data", health.getData());
        object.addProperty("_rev", health.get_rev());
        return object;
    }
}
