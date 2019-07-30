package org.ole.planet.myplanet.model;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmMyLife extends RealmObject {
    private int weight;
    @PrimaryKey
    private String _id;
    private int imageId;
    private String userId;
    private String title;
    private int isVisible;

    public static void insertMyLife(String userId, JsonObject myLifeDoc, Realm mRealm) {
        Utilities.log("INSERT MYLIFE " + new Gson().toJson(myLifeDoc));
        String stringId = JsonUtils.getString("_id", myLifeDoc);
        RealmMyLife myMyLifeDB = mRealm.where(RealmMyLife.class).equalTo("id", stringId).findFirst();
        if (myMyLifeDB == null) {
            myMyLifeDB = mRealm.createObject(RealmMyLife.class, stringId);
        }
        myMyLifeDB.setUserId(userId);
        myMyLifeDB.set_id(JsonUtils.getString("_id", myLifeDoc));
        myMyLifeDB.setImageId(JsonUtils.getInt("imageId",myLifeDoc));
        myMyLifeDB.setTitle(JsonUtils.getString("title",myLifeDoc));
        myMyLifeDB.setWeight(JsonUtils.getInt("weight",myLifeDoc));
    }

//    public static List<RealmMyLife> getMyLifeByUserId(String userId, List<RealmMyLife> myLifeList) {
//        List<RealmMyLife> myLifeItems = new ArrayList<>();
//        for (RealmMyLife item : myLifeList) {
//            if (item.getUserId().contains(userId)) {
//                myLifeItems.add(item);
//            }
//        }
//        return myLifeItems;
//    }

    public static List<RealmObject> getMyLifeByUserId(Realm mRealm, SharedPreferences settings) {
        String userId = settings.getString("userId", "--");
        List <RealmMyLife> myLifeList = mRealm.where(RealmMyLife.class).findAll().sort("weight");
        List<RealmObject> myLifeItems = new ArrayList<>();
        for (RealmMyLife item : myLifeList) {
            if (item.getUserId().contains(userId)) {
                myLifeItems.add(item);
            }
        }
        return myLifeItems;
    }


    public static List<RealmObject> getMyLifeByUserId(Realm mRealm, String userId) {
        List <RealmMyLife> myLifeList = mRealm.where(RealmMyLife.class).findAll();
        List<RealmObject> myLifeItems = new ArrayList<>();
        for (RealmMyLife item : myLifeList) {
            if (item.getUserId().contains(userId)) {
                myLifeItems.add(item);
            }
        }
        return myLifeItems;
    }

    public static List<RealmMyLife> getMyLifeByU(Realm mRealm, String userId) {
        List <RealmMyLife> myLifeList = mRealm.where(RealmMyLife.class).findAll().sort("weight");
        List<RealmMyLife> myLifeItems = new ArrayList<>();
        for (RealmMyLife item : myLifeList) {
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

    public static void insert(Realm mRealm, JsonObject doc) {
        insertMyLife("", doc, mRealm);
    }

    public static RealmMyLife getMyLife(Realm mRealm, String stringId) {
        return mRealm.where(RealmMyLife.class).equalTo("_id", stringId).findFirst();
    }

    public static void createMyLife(RealmMyLife myLife, Realm mRealm, String _id) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        myLife.set_id(_id);
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

//    public static JsonArray getMyLifeIds(Realm realm, String stringId) {
//        List<RealmObject> myLifeItems = getMyLifeByUserId(stringId, realm.where(RealmMyLife.class).findAll());
//        JsonArray stringIds = new JsonArray();
//        for (RealmObject lib : myLifeItems
//        ) {
//            stringIds.add(((RealmMyLife) lib).get_id());
//        }
//        return stringIds;
//    }

    public RealmMyLife(int imageId, String userId, String title) {
        this.imageId = imageId;
        this.userId = userId;
        this.title = title;
        this.isVisible = 1;
    }

    public RealmMyLife() {
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public int getImageId() {
        return imageId;
    }

    public void setImageId(int imageId) {
        this.imageId = imageId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(int isVisible) {
        this.isVisible = isVisible;
    }

    public static void updateWeight(int weight,String title, Realm realm, String userId){
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                int currentWeight = -1;
                List <RealmMyLife> myLifeList = realm.where(RealmMyLife.class).findAll();
                for (RealmMyLife item : myLifeList) {
                    if (item.getUserId().contains(userId)) {
                        if(item.getTitle().contains(title)){
                            currentWeight =item.getWeight();
                            item.setWeight(weight);
                        }
                    }
                }
                for (RealmMyLife item : myLifeList) {
                    if (item.getUserId().contains(userId)) {
                        if (currentWeight != -1 && item.getWeight() == weight && !item.getTitle().contains(title)) {
                            item.setWeight(currentWeight);
                        }
                    }
                }
            }
        });

    }
}
