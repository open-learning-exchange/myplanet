package org.ole.planet.myplanet.model;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmTeamNotification extends RealmObject {
    @PrimaryKey
    String id;
    String type;
    String parentId;
    int lastCount;

    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public int getLastCount() {
        return lastCount;
    }

    public void setLastCount(int lastCount) {
        this.lastCount = lastCount;
    }

    public void setType(String type) {
        this.type = type;
    }

}
