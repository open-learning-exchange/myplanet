package org.ole.planet.myplanet.model;

import android.nfc.Tag;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.PrimaryKey;


public class RealmTag extends RealmObject {

    @PrimaryKey
    private String id;
    private String _rev;

    private String name;

    private String _id;

    private RealmList<String> attachedTo;


    public static HashMap<String, RealmTag> getListAsMap(List<RealmTag> list) {
        HashMap<String, RealmTag> map = new HashMap<>();
        for (RealmTag r : list
        ) {
            map.put(r.get_id(), r);
        }
        return map;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

//    public List<RealmTag> getChild() {
//        return child;
//    }
//
//    public void setChild(List<RealmTag> child) {
//
//        this.child = child;
//    }
//
//    public void setChild(RealmTag tag) {
//        if (child == null) {
//            child = new ArrayList<>();
//        }
//        if (!this.child.contains(tag))
//            this.child.add(tag);
//
//    }

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

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }


    public static void insertTags(Realm mRealm, JsonObject act) {
        RealmTag tag = mRealm.where(RealmTag.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (tag == null)
            tag = mRealm.createObject(RealmTag.class, JsonUtils.getString("_id", act));
        tag.set_rev(JsonUtils.getString("_rev", act));
        tag.set_id(JsonUtils.getString("_id", act));
        tag.setName(JsonUtils.getString("name", act));
        tag.setAttachedTo(JsonUtils.getJsonArray("attachedTo", act));
    }

    private void setAttachedTo(JsonArray attachedTo) {
        this.attachedTo = new RealmList<>();
        for (int i = 0; i < attachedTo.size(); i++) {
            this.attachedTo.add(JsonUtils.getString(attachedTo, i));
        }
    }

    @Override
    public String toString() {
        return name;
    }

    public RealmList<String> getAttachedTo() {
        return attachedTo;
    }

    public void setAttachedTo(RealmList<String> attachedTo) {
        this.attachedTo = attachedTo;
    }
}