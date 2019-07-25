package org.ole.planet.myplanet.model;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmMyLife extends RealmObject {
    @PrimaryKey
    private String stringId;
    private String imageId;
    private RealmList<String> userId;

    public static void insertMyLife(String userId, JsonObject myLifeDoc, Realm mRealm) {
        Utilities.log("INSERT MYLIFE " + new Gson().toJson(myLifeDoc));
        String stringId = JsonUtils.getString("_id", myLifeDoc);
        RealmMyLife myMyLifeDB = mRealm.where(RealmMyLife.class).equalTo("id", stringId).findFirst();
        if (myMyLifeDB == null) {
            myMyLifeDB = mRealm.createObject(RealmMyLife.class, stringId);
        }
        myMyLifeDB.setUserId(userId);
        myMyLifeDB.setStringId(JsonUtils.getString("_id", myLifeDoc));
        myMyLifeDB.setImageId(JsonUtils.getString("imageId",myLifeDoc));
    }

    public static List<RealmObject> getMyByUserId(Realm mRealm, SharedPreferences settings) {
        RealmResults<RealmMyLife> libs = mRealm.where(RealmMyLife.class).findAll();
        List<RealmObject> myLifeItems = new ArrayList<>();
        for (RealmMyLife item : libs) {
            if (item.getUserId().contains(settings.getString("userId", "--"))) {
                myLifeItems.add(item);
            }
        }
        return myLifeItems;
    }


    public static List<RealmMyLife> getMyLifeByUserId(String userId, List<RealmMyLife> libs) {
        List<RealmMyLife> myLifeItems = new ArrayList<>();
        for (RealmMyLife item : libs) {
            if (item.getUserId().contains(userId)) {
                myLifeItems.add(item);
            }
        }
        return myLifeItems;
    }

    public static List<RealmMyLife> getMyLifeAll(String userId, List<RealmMyLife> libs) {
        List<RealmMyLife> myLifeAll = new ArrayList<>();
        for (RealmMyLife item : libs) {
            if (!item.getUserId().contains(userId)) {
                myLifeAll.add(item);
            }
        }
        return myLifeAll;
    }


    public static boolean isMyCourse(String userId, String courseId, Realm realm) {
        return RealmMyLife.getMyLifeByUserId(userId, realm.where(RealmMyLife.class).equalTo("courseId", courseId).findAll()).size() > 0;
    }

    public static void insert(Realm mRealm, JsonObject doc) {
        insertMyLife("", doc, mRealm);
    }

    public static RealmMyLife getMyLife(Realm mRealm, String stringId) {
        return mRealm.where(RealmMyLife.class).equalTo("_id", stringId).findFirst();
    }

    public static void createMyLife(RealmMyLife myLife, Realm mRealm, String stringId) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        myLife.setStringId(stringId);
        mRealm.commitTransaction();
    }
//
//    public static String[] getMyCourseIds(Realm mRealm, String userId) {
//        List<RealmMyLife> list = mRealm.where(RealmMyLife.class).equalTo("userId", userId).findAll();
//        String[] myIds = new String[list.size()];
//        for (int i = 0; i < list.size(); i++) {
//            myIds[i] = list.get(i).getCourseId();
//        }
//        return myIds;
//    }

    public static JsonArray getMyLifeIds(Realm realm, String stringId) {
        List<RealmMyLife> myLifeItems = getMyLifeByUserId(stringId, realm.where(RealmMyLife.class).findAll());
        JsonArray stringIds = new JsonArray();
        for (RealmObject lib : myLifeItems
        ) {
            stringIds.add(((RealmMyLife) lib).getStringId());
        }
        return stringIds;
    }

    public String getStringId() {
        return stringId;
    }

    public void setStringId(String stringId) {
        this.stringId = stringId;
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public RealmList<String> getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        if (this.userId == null) {
            this.userId = new RealmList<>();
        }

        if (!this.userId.contains(userId) && !TextUtils.isEmpty(userId))
            this.userId.add(userId);
    }
}
