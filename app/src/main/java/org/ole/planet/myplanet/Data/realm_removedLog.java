package org.ole.planet.myplanet.Data;


import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_removedLog extends RealmObject {

    @PrimaryKey
    private String id;
    private String userId;
    private String type;
    private String docId;

    public static void onAdd(Realm mRealm, String type, String userId, String docId) {
      if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        mRealm.where(realm_removedLog.class).equalTo("type", type).equalTo("userId", userId).equalTo("docId", docId).findAll().deleteAllFromRealm();
        mRealm.commitTransaction();
    }

    public static void onRemove(Realm mRealm, String type, String userId, String docId) {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        realm_removedLog log = mRealm.createObject(realm_removedLog.class, UUID.randomUUID().toString());
        log.setDocId(docId);
        log.setUserId(userId);
        log.setType(type);
        mRealm.commitTransaction();
    }

    public static String[] removedIds(Realm realm, String type, String userId){
        List<realm_removedLog> removedLibs = realm.where(realm_removedLog.class).equalTo("userId", userId).equalTo("type", type).findAll();
        if (removedLibs!=null){
            String[] ids = new String[removedLibs.size()];
            int i = 0;
            for (realm_removedLog removed: removedLibs) {
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
