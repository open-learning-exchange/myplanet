package org.ole.planet.takeout;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.ole.planet.takeout.Data.realm_courseSteps;
import org.ole.planet.takeout.Data.realm_examQuestion;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.Data.realm_stepResources;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

public abstract class CustomDataProcessing extends AppCompatActivity {
    CouchDbClientAndroid dbResources, dbMeetup, dbMyCourses, generaldb;
    SharedPreferences settings;
    Realm mRealm;
    CouchDbProperties properties;

    public void setVariables(SharedPreferences settings, Realm mRealm, CouchDbProperties properties) {
        this.settings = settings;
        this.mRealm = mRealm;
        this.properties = properties;
    }

    public void check(String[] stringArray, JsonArray array_categoryIds, Class aClass, RealmResults<?> db_Categrory) {
        for (int x = 0; x < array_categoryIds.size(); x++) {
            db_Categrory = mRealm.where(aClass)
                    .equalTo("userId", stringArray[0])
                    .equalTo(stringArray[1], array_categoryIds.get(x).getAsString())
                    .findAll();
            if (db_Categrory.isEmpty()) {
                setRealmProperties(stringArray[2]);
                generaldb = new CouchDbClientAndroid(properties);
                JsonObject resourceDoc = generaldb.find(JsonObject.class, array_categoryIds.get(x).getAsString());
                triggerInsert(stringArray, array_categoryIds, x, resourceDoc);
            } else {
                Log.e("DATA", " Data already saved for -- " + stringArray[0] + " " + array_categoryIds.get(x).getAsString());
            }
        }
    }

    public void triggerInsert(String[] stringArray, JsonArray array_categoryIds, int x, JsonObject resourceDoc) {
        switch (stringArray[2]) {
            case "resources":
                insertMyLibrary(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc);
                break;
            case "meetups":
                insertMyMeetups(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc);
                break;
            case "courses":
                insertMyCourses(stringArray[0], array_categoryIds.get(x).getAsString(), resourceDoc);
                break;
        }
    }

    public void checkMyTeams(String userId, JsonArray array_myTeamIds) {
        for (int tms = 0; tms < array_myTeamIds.size(); tms++) {
        }
    }


    public void setRealmProperties(String dbName) {
        properties.setDbName(dbName);
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
    }

    public void insertMyLibrary(String userId, String resourceID, JsonObject resourceDoc) {
        realm_myLibrary myLibraryDB = mRealm.createObject(realm_myLibrary.class, UUID.randomUUID().toString());
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
        JsonObject attachments = resourceDoc.get("_attachments").getAsJsonObject();
        JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(String.valueOf(attachments));
        JsonObject obj = element.getAsJsonObject();
        Set<Map.Entry<String, JsonElement>> entries = obj.entrySet();
        for (Map.Entry<String, JsonElement> entry : entries) {
            if (entry.getKey().indexOf("/") < 0) {
                myLibraryDB.setResourceRemoteAddress(settings.getString("serverURL", "http://") + "/resources/" + resourceID + "/" + entry.getKey());
                myLibraryDB.setResourceLocalAddress(entry.getKey());
                myLibraryDB.setResourceOffline(false);
            }
        }
        myLibraryDB.setDescription(resourceDoc.get("description").getAsString());
    }

    public void insertMyMeetups(String userId, String meetupID, JsonObject meetupDoc) {
        realm_meetups myMeetupsDB = mRealm.createObject(realm_meetups.class, UUID.randomUUID().toString());
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

    public void insertMyCourses(String userId, String myCoursesID, JsonObject myCousesDoc) {
        realm_myCourses myMyCoursesDB = mRealm.createObject(realm_myCourses.class, UUID.randomUUID().toString());
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
        myMyCoursesDB.setnumberOfSteps(myCousesDoc.get("steps").getAsJsonArray().size());
        insertCourseSteps(myCoursesID, myCousesDoc.get("steps").getAsJsonArray(), myCousesDoc.get("steps").getAsJsonArray().size());
    }

    public void insertCourseSteps(String myCoursesID, JsonArray steps, int numberOfSteps) {
        for (int step = 0; step < numberOfSteps; step++) {
            String step_id = UUID.randomUUID().toString();
            realm_courseSteps myCourseStepDB = mRealm.createObject(realm_courseSteps.class, step_id);
            myCourseStepDB.setCourseId(myCoursesID);
            JsonObject stepContainer = steps.get(step).getAsJsonObject();
            myCourseStepDB.setStepTitle(stepContainer.get("stepTitle").getAsString());
            myCourseStepDB.setDescription(stepContainer.get("description").getAsString());
            if (stepContainer.has("resources")) {
                myCourseStepDB.setNoOfResources(stepContainer.get("resources").getAsJsonArray().size());
                insertCourseStepsAttachments(myCoursesID, step_id, stepContainer.getAsJsonArray("resources"));
            }
            // myCourseStepDB.setNoOfResources(stepContainer.get("exam").getAsJsonArray().size());
            if (stepContainer.has("exam"))
                insertCourseStepsExams(myCoursesID, step_id, stepContainer.getAsJsonObject("exam"));
        }
    }

    public void insertCourseStepsExams(String myCoursesID, String step_id, JsonObject exam) {
        realm_stepExam myExam = mRealm.createObject(realm_stepExam.class, exam.get("_id").getAsString());
        myExam.setStepId(step_id);
        myExam.setCourseId(myCoursesID);
        myExam.setName(exam.get("name").getAsString());
        if (exam.has("passingPercentage"))
            myExam.setPassingPercentage(exam.get("passingPercentage").getAsString());
        if (exam.has("totalMarks"))
            myExam.setPassingPercentage(exam.get("totalMarks").getAsString());
        if (exam.has("questions"))
            insertExamQuestions(exam.get("questions").getAsJsonArray(), exam.get("_id").getAsString());
    }

    private void insertExamQuestions(JsonArray questions, String examId) {
        for (int i = 0; i < questions.size(); i++) {
            String questionId = UUID.randomUUID().toString();
            JsonObject question = questions.get(i).getAsJsonObject();
            realm_examQuestion myQuestion = mRealm.createObject(realm_examQuestion.class, questionId);
            myQuestion.setExamId(examId);
            myQuestion.setBody(question.get("body").getAsString());
            myQuestion.setType(question.get("type").getAsString());
            myQuestion.setHeader(question.get("header").getAsString());
            myQuestion.setChoice(question.get("choices").getAsJsonArray());


            Utilities.log("Exam Inserted  " + question.get("header").getAsString());

        }
    }

    public void insertCourseStepsAttachments(String myCoursesID, String stepId, JsonArray resources) {
        for (int i = 0; i < resources.size(); i++) {
            JsonObject res = resources.get(i).getAsJsonObject();
            realm_stepResources myResource = mRealm.createObject(realm_stepResources.class, res.get("_id").getAsString());
            myResource.setCourseId(myCoursesID);
            myResource.setStepId(stepId);
            myResource.setResource_rev(res.get("_rev").getAsString());
            myResource.setTitle(res.get("title").getAsString());
            myResource.setAuthor(res.get("author").getAsString());
            myResource.setDescription(res.get("description").getAsString());
            myResource.setYear(res.get("year").getAsString());
            myResource.setLanguage(res.get("language").getAsString());
            myResource.setPublisher(res.get("publisher").getAsString());
            myResource.setLinkToLicense(res.get("linkToLicense").getAsString());
            myResource.setSubject(res.get("subject").getAsString());
            myResource.setLevel(res.get("level").getAsString());
            myResource.setOpenWith(res.get("openWith").getAsString());
            myResource.setMedium(res.get("medium").getAsString());
            myResource.setArticleDate(res.get("articleDate").getAsString());
            myResource.setResourceType(res.get("resourceType").getAsString());
            myResource.setAddedBy(res.get("addedBy").getAsString());
//            myResource.setOpenUrl(res.get("openUrl").getAsString());
            myResource.setOpenWhichFile(res.get("openWhichFile").getAsString());
            myResource.setIsDownloadable(res.get("isDownloadable").getAsString());
            myResource.setFilename(res.get("filename").getAsString());
            myResource.setResourceLocalAddress(res.get("filename").getAsString());
            myResource.setResourceRemoteAddress(Utilities.getUrl(res.get("_id").getAsString(), res.get("filename").getAsString(), settings));

            Utilities.log("Resource Inserted  " + res.get("filename").getAsString());
        }
    }

    public void insertMyTeams(realm_meetups myMyTeamsDB, String userId, String myTeamsID, JsonObject myTeamsDoc) {

    }
}
