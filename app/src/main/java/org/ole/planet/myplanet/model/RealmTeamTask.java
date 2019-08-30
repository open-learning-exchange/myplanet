package org.ole.planet.myplanet.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.security.PrivateKey;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmTeamTask extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id, _rev, title, deadline, description, link, sync, teamId;
    private boolean completed;


    public static void insert(Realm mRealm, JsonObject obj) {
        Utilities.log("Sunc tasks");
        RealmTeamTask task = mRealm.createObject(RealmTeamTask.class, UUID.randomUUID().toString());
        task.set_id(JsonUtils.getString("_id", obj));
        task.set_rev(JsonUtils.getString("_rev", obj));
        task.setTitle(JsonUtils.getString("title", obj));
        task.setDescription(JsonUtils.getString("description", obj));
        task.setLink(new Gson().toJson(JsonUtils.getJsonObject("link", obj)));
        task.setSync(new Gson().toJson(JsonUtils.getJsonObject("sync", obj)));
        task.setTeamId(JsonUtils.getString("teams", JsonUtils.getJsonObject("link", obj)));
        task.setCompleted(JsonUtils.getBoolean("completed", obj));
    }


    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getSync() {
        return sync;
    }

    public void setSync(String sync) {
        this.sync = sync;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
