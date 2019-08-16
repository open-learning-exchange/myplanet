package org.ole.planet.myplanet.model;

import com.google.gson.JsonObject;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmSubmitPhotos extends RealmObject {


    @PrimaryKey
    public String id;
    public String json_id;
    public String rev;
    public String submission_id;
    public String course_id;
    public String exam_id;
    public String member_id;
    public String mac_address;
    public String date;
    public String photo_file_path;
    public boolean uploaded;



    public static JsonObject serializeRealmSubmitPhotos(RealmSubmitPhotos submit)
    {
        JsonObject json = new JsonObject();
        json.addProperty("id", submit.getId());
        json.addProperty("submission_id", submit.getSubmission_id());
        json.addProperty("course_id", submit.getCourse_id());
        json.addProperty("exam_id", submit.getExam_id());
        json.addProperty("member_id", submit.getMember_id());
        json.addProperty("mac_address", submit.getMac_address());
        json.addProperty("date", submit.getDate());
        json.addProperty("photo_path", submit.getPhoto_file_path());

        return json;


    }

    public void setJson_id(String json_id) {
        this.json_id = json_id;
    }

    public void setRev(String rev) {
        this.rev = rev;
    }

    public void setPhoto_file_path(String photo_file_path) {
        this.photo_file_path = photo_file_path;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSubmission_id(String submission_id) {
        this.submission_id = submission_id;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getSubmission_id() {
        return submission_id;
    }

    public void setCourse_id(String course_id) {
        this.course_id = course_id;
    }

    public void setExam_id(String exam_id) {
        this.exam_id = exam_id;
    }

    public void setMember_id(String member_id) {
        this.member_id = member_id;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public void setMac_address(String mac_address) {
        this.mac_address = mac_address;
    }

    public String getMac_address() {
        return mac_address;
    }

    public String getCourse_id() {
        return course_id;
    }

    public String getExam_id() {
        return exam_id;
    }

    public String getMember_id() {
        return member_id;
    }

    public String getDate() {
        return date;
    }

    public String getJson_id() {
        return json_id;
    }

    public String getRev() {
        return rev;
    }

    public String getId() {
        return id;
    }


    public String getPhoto_file_path() {
        return photo_file_path;
    }

    public boolean isUploaded() {
        return uploaded;
    }
}




