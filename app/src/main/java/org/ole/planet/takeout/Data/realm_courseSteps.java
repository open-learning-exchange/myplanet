package org.ole.planet.takeout.Data;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_courseSteps extends RealmObject {
    @PrimaryKey
    private String id;
    private String courseId;
    private String stepTitle;
    private String description;
    private String noOfResources;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getStepTitle() {
        return stepTitle;
    }

    public void setStepTitle(String stepTitle) {
        this.stepTitle = stepTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNoOfResources() {
        return noOfResources;
    }

    public void setNoOfResources(String noOfResources) {
        this.noOfResources = noOfResources;
    }
}
