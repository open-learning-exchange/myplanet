package org.ole.planet.takeout.Data;


import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_stepExam extends RealmObject {
    @PrimaryKey
    private String id;
    private String name;
    private String stepId;
    private String courseId;
    private String passingPercentage;
    private String totalMarks;

    public String getPassingPercentage() {
        return passingPercentage;
    }

    public void setPassingPercentage(String passingPercentage) {
        this.passingPercentage = passingPercentage;
    }

    public String getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(String totalMarks) {
        this.totalMarks = totalMarks;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
