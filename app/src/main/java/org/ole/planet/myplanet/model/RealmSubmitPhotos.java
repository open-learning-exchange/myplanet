package org.ole.planet.myplanet.model;

import com.google.gson.JsonObject;

import io.realm.RealmObject;

public class RealmSubmitPhotos extends RealmObject {
    @io.realm.annotations.PrimaryKey
    private String id;
    private String _id;
    private String _rev;
    private String submissionId;
    private String courseId;
    private String ExamId;
    private String memberId;
    private String date;
    private String uniqueId;
    private String photoLocation;
    private boolean uploaded;

    public void setId(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setExamId(String examId) {
        ExamId = examId;
    }

    public String getExamId() {
        return ExamId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDate() {
        return date;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setPhotoLocation(String photo_location) {
        this.photoLocation = photoLocation;
    }

    public String getPhotoLocation() {
        return photoLocation;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String get_rev() {
        return _rev;
    }

    public String get_id() {
        return _id;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public boolean getUploaded() {
        return uploaded;
    }

    /**
     * public static JsonArray serializeRealmSubmitPhotos(RealmList<RealmSubmitPhotos> submitPhotos)
     * {
     * JsonArray arr = new JsonArray();
     * for(RealmSubmitPhotos sub: submitPhotos)
     * {
     * arr.add(createObject(sub));
     * }
     * <p>
     * return arr;
     * }
     **/

    public static JsonObject serializeRealmSubmitPhotos(RealmSubmitPhotos submit) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", submit.getId());
        obj.addProperty("submissionId", submit.getSubmissionId());
        obj.addProperty("type", "photo");
        obj.addProperty("courseId", submit.getCourseId());
        obj.addProperty("examId", submit.getExamId());
        obj.addProperty("memberId", submit.getMemberId());
        obj.addProperty("date", submit.getDate());
        obj.addProperty("macAddress", submit.getUniqueId());
        obj.addProperty("photoLocation", submit.getPhotoLocation());

        return obj;
    }
}
