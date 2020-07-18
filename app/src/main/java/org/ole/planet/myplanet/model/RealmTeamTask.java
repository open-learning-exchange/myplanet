package org.ole.planet.myplanet.model;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.security.PrivateKey;
import java.sql.Time;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmTeamTask extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id, _rev, title, description, link, sync, teamId;
    private boolean updated;
    private String assignee;
    private long deadline;
    private long completedTime;
    private String status;
    private boolean completed;
    private boolean notified;

    public static void insert(Realm mRealm, JsonObject obj) {
        RealmTeamTask task = mRealm.where(RealmTeamTask.class).equalTo("_id", JsonUtils.getString("_id", obj)).findFirst();
        if (task == null) {
            task = mRealm.createObject(RealmTeamTask.class, JsonUtils.getString("_id", obj));
        }
        task.set_id(JsonUtils.getString("_id", obj));
        task.set_rev(JsonUtils.getString("_rev", obj));
        task.setTitle(JsonUtils.getString("title", obj));
        task.setStatus(JsonUtils.getString("status", obj));
        task.setDeadline(JsonUtils.getLong("deadline", obj));
        task.setCompletedTime(JsonUtils.getLong("completedTime", obj));
        task.setDescription(JsonUtils.getString("description", obj));
        task.setLink(new Gson().toJson(JsonUtils.getJsonObject("link", obj)));
        task.setSync(new Gson().toJson(JsonUtils.getJsonObject("sync", obj)));
        task.setTeamId(JsonUtils.getString("teams", JsonUtils.getJsonObject("link", obj)));
        JsonObject user = JsonUtils.getJsonObject("assignee", obj);
        if (user.has("_id"))
            task.setAssignee(JsonUtils.getString("_id", user));
        task.setCompleted(JsonUtils.getBoolean("completed", obj));
    }

    public long getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(long completedTime) {
        this.completedTime = completedTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isNotified() {
        return notified;
    }


    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public static JsonObject serialize(Realm realm, RealmTeamTask task) {
        JsonObject object = new JsonObject();
        if (!TextUtils.isEmpty(task.get_id())){
            object.addProperty("_id", task.get_id());
            object.addProperty("_rev", task.get_rev());
        }
        object.addProperty("title", task.getTitle());
        object.addProperty("deadline", task.getDeadline());
        object.addProperty("description", task.getDescription());
        object.addProperty("completed", task.isCompleted());
        object.addProperty("completedTime", task.getCompletedTime());
        RealmUserModel user = realm.where(RealmUserModel.class).equalTo("id", task.getAssignee()).findFirst();
        if (user != null)
            object.add("assignee", user.serialize());
        else
            object.addProperty("assignee", "");
        object.add("sync", new Gson().fromJson(task.getSync(), JsonObject.class));
        object.add("link", new Gson().fromJson(task.getLink(), JsonObject.class));
        return object;
    }


    public boolean isUpdated() {
        return updated;
    }

    public void setUpdated(boolean updated) {
        this.updated = updated;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
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

    @Override
    public String toString() {
        return title;
    }
}
