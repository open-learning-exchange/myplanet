package org.ole.planet.takeout;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.ole.planet.takeout.Data.realm_courseSteps;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;

import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

public abstract class CustomDataProcessing extends AppCompatActivity {
    CouchDbClientAndroid dbResources, dbMeetup, dbMyCourses;
    SharedPreferences settings;
    Realm mRealm;
    CouchDbProperties properties;

    public void setVariables(SharedPreferences settings, Realm mRealm, CouchDbProperties properties) {
        this.settings = settings;
        this.mRealm = mRealm;
        this.properties = properties;
    }

    public void checkMyLibrary(String userId, JsonArray array_resourceIds) {
        for (int x = 0; x < array_resourceIds.size(); x++) {
            RealmResults<realm_myLibrary> db_myLibrary = mRealm.where(realm_myLibrary.class)
                    .equalTo("userId", userId)
                    .equalTo("resourceId", array_resourceIds.get(x).getAsString())
                    .findAll();
            Log.e("DATA", db_myLibrary.toString());
            if (db_myLibrary.isEmpty()) {
                realm_myLibrary myLibraryDB = mRealm.createObject(realm_myLibrary.class, UUID.randomUUID().toString());
                properties.setDbName("resources");
                properties.setUsername(settings.getString("url_user", ""));
                properties.setPassword(settings.getString("url_pwd", ""));
                dbResources = new CouchDbClientAndroid(properties);
                JsonObject resourceDoc = dbResources.find(JsonObject.class, array_resourceIds.get(x).getAsString());
                insertMyLibrary(myLibraryDB, userId, array_resourceIds.get(x).getAsString(), resourceDoc);
            } else {
                Log.e("DATA", " Resource data already saved for -- " + userId + " " + array_resourceIds.get(x).getAsString());
            }

        }
    }

    public void checkMyMeetups(String userId, JsonArray array_meetupIds) {
        for (int x = 0; x < array_meetupIds.size(); x++) {
            RealmResults<realm_meetups> db_myMeetups = mRealm.where(realm_meetups.class)
                    .equalTo("meetupId", array_meetupIds.get(x).getAsString())
                    .equalTo("userId", userId)
                    .findAll();
            if (db_myMeetups.isEmpty()) {
                realm_meetups myMeetupsDB = mRealm.createObject(realm_meetups.class, UUID.randomUUID().toString());
                properties.setDbName("meetups");
                properties.setUsername(settings.getString("url_user", ""));
                properties.setPassword(settings.getString("url_pwd", ""));
                dbMeetup = new CouchDbClientAndroid(properties);
                JsonObject meetupDoc = dbMeetup.find(JsonObject.class, array_meetupIds.get(x).getAsString());
                insertMyMeetups(myMeetupsDB, userId, array_meetupIds.get(x).getAsString(), meetupDoc);
            }

        }
    }

    public void checkMyCourses(String userId, JsonArray array_courseIds) {
        for (int x = 0; x < array_courseIds.size(); x++) {
            RealmResults<realm_meetups> db_myCourses = mRealm.where(realm_meetups.class)
                    .equalTo("userId", userId)
                    .equalTo("courseId", array_courseIds.get(x).getAsString())
                    .findAll();
            if (db_myCourses.isEmpty()) {
                realm_myCourses myCoursesDB = mRealm.createObject(realm_myCourses.class, UUID.randomUUID().toString());
                properties.setDbName("courses");
                properties.setPassword(settings.getString("url_pwd", ""));
                properties.setUsername(settings.getString("url_user", ""));
                dbMyCourses = new CouchDbClientAndroid(properties);
                JsonObject myCoursesDoc = dbMyCourses.find(JsonObject.class, array_courseIds.get(x).getAsString());
                insertMyCourses(myCoursesDB, userId, array_courseIds.get(x).getAsString(), myCoursesDoc);
            }

        }
    }

    public void checkMyTeams(String userId, JsonArray array_myTeamIds) {
    }



    public void insertMyLibrary(realm_myLibrary myLibraryDB, String userId, String resourceID, JsonObject resourceDoc) {
        Log.e("Inserting", resourceDoc.toString());
        myLibraryDB.setUserId(userId);
        myLibraryDB.setResourceId(resourceID);
        myLibraryDB.setResource_rev(resourceDoc.get("_rev").getAsString());
        myLibraryDB.setTitle(resourceDoc.get("title").getAsString());
        myLibraryDB.setAuthor(resourceDoc.get("author").getAsString());
//        myLibraryDB.setPublisher(resourceDoc.get("Publisher").getAsString());
//        myLibraryDB.setMedium(resourceDoc.get("medium").getAsString());
        myLibraryDB.setLanguage(resourceDoc.get("language").getAsString()); //array
        myLibraryDB.setSubject(resourceDoc.get("subject").getAsString()); // array
//        myLibraryDB.setLinkToLicense(resourceDoc.get("linkToLicense").getAsString());
//        myLibraryDB.setResourceFor(resourceDoc.get("resourceFor")!= null ? resourceDoc.get("resourceFor").getAsString() : "");
        myLibraryDB.setMediaType(resourceDoc.get("mediaType").getAsString());
//        myLibraryDB.setAverageRating(resourceDoc.get("averageRating").getAsString());
        myLibraryDB.setDescription(resourceDoc.get("description").getAsString());
    }

    public void insertMyMeetups(realm_meetups myMeetupsDB, String userId, String meetupID, JsonObject meetupDoc) {
        myMeetupsDB.setUserId(userId);
        myMeetupsDB.setMeetupId(meetupID);
        myMeetupsDB.setMeetupId_rev(meetupDoc.get("meetupId_rev").getAsString());
        myMeetupsDB.setTitle(meetupDoc.get("title").getAsString());
        myMeetupsDB.setDescription(meetupDoc.get("description").getAsString());
        myMeetupsDB.setStartDate(meetupDoc.get("startDate").getAsString());
        myMeetupsDB.setEndDate(meetupDoc.get("endDate").getAsString());
        myMeetupsDB.setRecurring(meetupDoc.get("recurring").getAsString());
        myMeetupsDB.setDay(meetupDoc.get("Day").getAsString());
        myMeetupsDB.setStartTime(meetupDoc.get("startTime").getAsString());
        myMeetupsDB.setCategory(meetupDoc.get("category").getAsString());
        myMeetupsDB.setMeetupLocation(meetupDoc.get("meetupLocation").getAsString());
        myMeetupsDB.setCreator(meetupDoc.get("creator").getAsString());
    }

    public void insertMyCourses(realm_myCourses myMyCoursesDB, String userId, String myCoursesID, JsonObject myCousesDoc) {
        myMyCoursesDB.setUserId(userId);
        myMyCoursesDB.setCourseId(myCoursesID);
        myMyCoursesDB.setCourse_rev(myCousesDoc.get("_rev").getAsString());
        myMyCoursesDB.setLanguageOfInstruction(myCousesDoc.get("languageOfInstruction").getAsString());
        myMyCoursesDB.setCourse_rev(myCousesDoc.get("courseTitle").getAsString());
        myMyCoursesDB.setMemberLimit(myCousesDoc.get("memberLimit").getAsInt());
        myMyCoursesDB.setDescription(myCousesDoc.get("description").getAsString());
        myMyCoursesDB.setMethod(myCousesDoc.get("method").getAsString());
        myMyCoursesDB.setGradeLevel(myCousesDoc.get("gradeLevel").getAsString());
        myMyCoursesDB.setSubjectLevel(myCousesDoc.get("subjectLevel").getAsString());
        myMyCoursesDB.setCreatedDate(myCousesDoc.get("createdDate").getAsString());
        myMyCoursesDB.setnumberOfSteps(myCousesDoc.get("numberOfSteps").getAsJsonArray().size());
        insertCourseSteps(myCoursesID, myCousesDoc.get("steps").getAsJsonArray(), myCousesDoc.get("numberOfSteps").getAsJsonArray().size());
    }

    public void insertCourseSteps(String myCoursesID, JsonArray steps, int numberOfSteps) {
        for (int step = 0; step < numberOfSteps; step++) {
            String step_id = UUID.randomUUID().toString();
            realm_courseSteps myCourseStepDB = mRealm.createObject(realm_courseSteps.class, step_id);
            myCourseStepDB.setCourseId(myCoursesID);
            JsonObject stepContainer = steps.get(step).getAsJsonObject();
            myCourseStepDB.setStepTitle(stepContainer.get("stepTitle").getAsString());
            myCourseStepDB.setDescription(stepContainer.get("description").getAsString());
            myCourseStepDB.setNoOfResources(stepContainer.get("attachment").getAsJsonArray().size());
            myCourseStepDB.setNoOfResources(stepContainer.get("exam").getAsJsonArray().size());
            insertCourseStepsAttachments();
            insertCourseStepsExams();
        }
    }

    public void insertCourseStepsExams() {

    }

    public void insertCourseStepsAttachments() {

    }

    public void insertMyTeams(realm_meetups myMyTeamsDB, String userId, String myTeamsID, JsonObject myTeamsDoc) {

    }
}
