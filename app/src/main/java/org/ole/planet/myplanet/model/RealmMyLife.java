package org.ole.planet.myplanet.model;

import android.content.SharedPreferences;
import java.util.List;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMyLife extends RealmObject {

    @PrimaryKey
    private String _id;
    private String imageId;
    private String userId;
    private String title;
    private boolean isVisible;
    private int weight;

    public static List<RealmMyLife> getMyLifeByUserId(Realm mRealm, SharedPreferences settings) {
        String userId = settings.getString("userId", "--");
        return getMyLifeByUserId(mRealm, userId);
    }

    public static List<RealmMyLife> getMyLifeByUserId(Realm mRealm, String userId) {
        return mRealm.where(RealmMyLife.class).equalTo("userId", userId).findAll().sort("weight");
    }

    public static void createMyLife(RealmMyLife myLife, Realm mRealm, String _id) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        myLife.set_id(_id);
        mRealm.commitTransaction();
    }

    public RealmMyLife(String imageId, String userId, String title) {
        this.imageId = imageId;
        this.userId = userId;
        this.title = title;
        this.isVisible = true;
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

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
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

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        isVisible = visible;
    }

    public static void updateWeight(int weight, String _id, Realm realm, String userId) {
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                int currentWeight = -1;
                List<RealmMyLife> myLifeList = getMyLifeByUserId(realm,userId);
                for (RealmMyLife item : myLifeList) {
                    if (item.get_id().contains(_id)) {
                        currentWeight = item.getWeight();
                        item.setWeight(weight);
                    }
                }
                for (RealmMyLife item : myLifeList) {
                    if (currentWeight != -1 && item.getWeight() == weight && !item.get_id().contains(_id)) {
                        item.setWeight(currentWeight);
                    }
                }
            }
        });
    }

    public static void updateVisibility(boolean isVisible, String _id, Realm realm, String userId) {
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                List<RealmMyLife> myLifeList = getMyLifeByUserId(realm,userId);
                for (RealmMyLife item : myLifeList) {
                    if (item.get_id().contains(_id)) {
                        item.setVisible(isVisible);
                    }
                }
            }
        });
    }
}
