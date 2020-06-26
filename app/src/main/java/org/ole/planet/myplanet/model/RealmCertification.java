package org.ole.planet.myplanet.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmCertification extends RealmObject {
    @PrimaryKey
    private
    String _id;
    private String _rev;
    private String name;
    private String courseIds;

    public static void insert(Realm mRealm, JsonObject object) {
        String id = JsonUtils.getString("_id", object);
        Utilities.log("certification insert");
        RealmCertification certification = mRealm.where(RealmCertification.class).equalTo("_id", id).findFirst();
        if (certification == null) {
            certification = mRealm.createObject(RealmCertification.class, id);
        }
        certification.setName(JsonUtils.getString("name", object));
        certification.setCourseIds(JsonUtils.getJsonArray("courseIds", object));

    }


    public static boolean isCourseCertified(Realm realm, String courseId) {
        long c = realm.where(RealmCertification.class).contains("courseIds", courseId).count();
        Utilities.log(c + " size");
        return c > 0;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCourseIds() {
        return courseIds;
    }

    public void setCourseIds(String courseIds) {
        this.courseIds = courseIds;
    }

    public void setCourseIds(JsonArray courseIds) {
       this.courseIds = new Gson().toJson(courseIds);
    }
}
