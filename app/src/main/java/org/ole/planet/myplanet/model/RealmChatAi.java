//package org.ole.planet.myplanet.model;
//
//import io.realm.RealmObject;
//import io.realm.annotations.PrimaryKey;
//
//public class RealmChatAi extends RealmObject {
//    @PrimaryKey
//    private String id;
//    private String _id;
//    private String _rev;
//    private String user;
//    private String time;
//    private String conversations;
//    private boolean uploaded;
//
//    public RealmChatAi(String id, String id1, String rev, String user, String time, String conversations) {
//        this.id = id;
//        this._id = id1;
//        this._rev = rev;
//        this.user = user;
//        this.time = time;
//        this.conversations = conversations;
//    }
//
//    public String getId() {
//        return id;
//    }
//
//    public void setId(String id) {
//        this.id = id;
//    }
//
//    public String get_id() {
//        return _id;
//    }
//
//    public void set_id(String _id) {
//        this._id = _id;
//    }
//
//    public String get_rev() {
//        return _rev;
//    }
//
//    public void set_rev(String _rev) {
//        this._rev = _rev;
//    }
//
//    public String getUser() {
//        return user;
//    }
//
//    public void setUser(String user) {
//        this.user = user;
//    }
//
//    public String getTime() {
//        return time;
//    }
//
//    public void setTime(String time) {
//        this.time = time;
//    }
//
//    public String getConversations() {
//        return conversations;
//    }
//
//    public void setConversations(String conversations) {
//        this.conversations = conversations;
//    }
//
//    public boolean isUploaded() {
//        return uploaded;
//    }
//
//    public void setUploaded(boolean uploaded) {
//        this.uploaded = uploaded;
//    }
//}
