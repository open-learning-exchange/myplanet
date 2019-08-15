package org.ole.planet.myplanet.model;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmSubmitPhotos extends RealmObject {


    @PrimaryKey
    public String id;
    public String submission_id;
    public String course_id;
    public String exam_id;
    public String member_id;
    public String mac_address;
    public String date;
    public String photo_file_path;


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


    public String getId() {
        return id;
    }


    public String getPhoto_file_path() {
        return photo_file_path;
    }
}




