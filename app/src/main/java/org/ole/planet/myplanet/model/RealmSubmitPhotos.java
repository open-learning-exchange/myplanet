package org.ole.planet.myplanet.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.realm.RealmList;
import io.realm.RealmObject;

public class RealmSubmitPhotos extends RealmObject {

  @io.realm.annotations.PrimaryKey
  private String id;
  private String _id;
  private String _rev;
  private String submission_id;
  private String course_id;
  private String Exam_id;
  private String member_id;
  private String date;
  private String mac_address;
  private String photo_location;
  private boolean uploaded;

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }


    public void setSubmission_id(String submission_id) {
        this.submission_id = submission_id;
    }

    public String getSubmission_id() {
        return submission_id;
    }

    public void setCourse_id(String course_id) {
        this.course_id = course_id;
    }

    public String getCourse_id() {
        return course_id;
    }


    public void setExam_id(String exam_id) {
        Exam_id = exam_id;
    }

    public String getExam_id() {
        return Exam_id;
    }

    public void setMember_id(String member_id) {
        this.member_id = member_id;
    }


    public String getMember_id() {
        return member_id;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDate() {
        return date;
    }

    public void setMac_address(String mac_address) {
        this.mac_address = mac_address;
    }

    public String getMac_address() {
        return mac_address;
    }


    public void setPhoto_location(String photo_location) {
        this.photo_location = photo_location;
    }

    public String getPhoto_location() {
        return photo_location;
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

    /** public static JsonArray serializeRealmSubmitPhotos(RealmList<RealmSubmitPhotos> submitPhotos)
    {
        JsonArray arr = new JsonArray();
        for(RealmSubmitPhotos sub: submitPhotos)
        {
            arr.add(createObject(sub));
        }

        return arr;
    }**/


    public static JsonObject serializeRealmSubmitPhotos(RealmSubmitPhotos submit)
    {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", submit.getId());
        obj.addProperty("submission_id", submit.getSubmission_id());
        obj.addProperty("type", "photo");
        obj.addProperty("course_id", submit.getCourse_id());
        obj.addProperty("exam_id", submit.getExam_id());
        obj.addProperty("member_id", submit.getMember_id());
        obj.addProperty("date", submit.getDate());
        obj.addProperty("mac_address", submit.getMac_address());
        obj.addProperty("photo_location", submit.getPhoto_location());

        return obj;
    }
}
