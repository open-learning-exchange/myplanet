package org.ole.planet.myplanet.model;


import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmRemovedLog extends RealmObject {

    @PrimaryKey
    private String id;
    private String userId;
    private String type;
    private String docId;

    public static void onAdd(Realm mRealm, String type, String userId, String docId) {
      if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        mRealm.where(RealmRemovedLog.class).equalTo("type", type).equalTo("userId", userId).equalTo("docId", docId).findAll().deleteAllFromRealm();
        mRealm.commitTransaction();
    }

    public static void onRemove(Realm mRealm, String type, String userId, String docId) {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmRemovedLog log = mRealm.createObject(RealmRemovedLog.class, UUID.randomUUID().toString());
        log.setDocId(docId);
        log.setUserId(userId);
        log.setType(type);
        mRealm.commitTransaction();
    }

    public static String[] removedIds(Realm realm, String type, String userId){
        List<RealmRemovedLog> removedLibs = realm.where(RealmRemovedLog.class).equalTo("userId", userId).equalTo("type", type).findAll();
        if (removedLibs!=null){
            String[] ids = new String[removedLibs.size()];
            int i = 0;
            for (RealmRemovedLog removed: removedLibs) {
                ids[i] = removed.getDocId();
                i++;
            }
            return ids;
        }
        return new String[0];
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }
}
