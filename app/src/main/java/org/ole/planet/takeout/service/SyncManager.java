package org.ole.planet.takeout.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_answerChoices;
import org.ole.planet.takeout.Data.realm_courseSteps;
import org.ole.planet.takeout.Data.realm_examQuestion;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.Data.realm_stepResources;
import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;

public class SyncManager {
    private static SyncManager ourInstance;
    private SharedPreferences settings;
    private Realm mRealm;
    private CouchDbProperties properties;
    private CouchDbClientAndroid generaldb;
    private Context context;
    private boolean isSyncing = false;
     static final String PREFS_NAME = "OLE_PLANET";
    private String[] stringArray = new String[3];
    private Document shelfDoc;

    public static SyncManager getInstance() {
        if (ourInstance == null) {
            ourInstance = new SyncManager(MainApplication.context);
        }
        return ourInstance;
    }


    private SyncManager(Context context) {
        this.context = context;
        settings  = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void realmConfig(String dbName) {
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();
        Realm.setDefaultConfiguration(config);
        mRealm = Realm.getInstance(config);
        properties = new CouchDbProperties()
                .setDbName(dbName)
                .setCreateDbIfNotExist(false)
                .setProtocol(settings.getString("url_Scheme", "http"))
                .setHost(settings.getString("url_Host", "192.168.2.1"))
                .setPort(settings.getInt("url_Port", 3000))
                .setUsername(settings.getString("url_user", ""))
                .setPassword(settings.getString("url_pwd", ""))
                .setMaxConnections(100)
                .setConnectionTimeout(0);
    }

    public void start() {
        if (!isSyncing) {
            syncDatabase();
        }else{
            Utilities.log("Already Syncing...");
        }
    }

    public void destroy(){
        isSyncing = false;
        ourInstance = null;

    }

    private void syncDatabase() {
        Thread td = new Thread(new Runnable() {
            public void run() {
                try {
                    isSyncing = true;
                    realmConfig("_users");
                    userTransactionSync(settings, mRealm, properties);
                    myLibraryTransactionSync();
                } finally {
                    isSyncing = false;
                    if (mRealm != null) {
                        mRealm.close();
                    }
                    destroy();
                }
            }
        });
        td.start();
    }

    private void setVariables(SharedPreferences settings, Realm mRealm, CouchDbProperties properties) {
        this.settings = settings;
        this.mRealm = mRealm;
        this.properties = properties;
    }

    private void userTransactionSync(SharedPreferences sett, Realm realm, CouchDbProperties propts) {
        properties = propts;
        settings = sett;
        mRealm = realm;
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                final CouchDbClientAndroid dbClient = new CouchDbClientAndroid(properties);
                final List<Document> allDocs = dbClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allDocs.size(); i++) {
                    Document doc = allDocs.get(i);
                    processUserDoc(dbClient, doc);
                }
            }
        });
    }

    private void processUserDoc(CouchDbClientAndroid dbClient, Document doc) {
        try {
            if (!doc.getId().equalsIgnoreCase("_design/_auth")) {
                JsonObject jsonDoc = dbClient.find(JsonObject.class, doc.getId());
                populateUsersTable(jsonDoc, mRealm);
                Log.e("Realm", " STRING " + jsonDoc.get("_id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void myLibraryTransactionSync() {
        properties.setDbName("shelf");
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                CouchDbClientAndroid dbShelfClient = new CouchDbClientAndroid(properties);
                List<Document> allShelfDocs = dbShelfClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allShelfDocs.size(); i++) {
                    shelfDoc = allShelfDocs.get(i);
                    populateShelfItems(settings, realm);
                }
            }
        });
    }


    private void populateUsersTable(JsonObject jsonDoc, Realm mRealm) {
        try {
            RealmResults<realm_UserModel> db_users = mRealm.where(realm_UserModel.class)
                    .equalTo("id", jsonDoc.get("_id").getAsString())
                    .findAll();
            if (db_users.isEmpty()) {
                realm_UserModel user = mRealm.createObject(realm_UserModel.class, jsonDoc.get("_id").getAsString());
                insertIntoUsers(jsonDoc, user);
            }


        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void insertIntoUsers(JsonObject jsonDoc, realm_UserModel user) {
        user.set_rev(jsonDoc.get("_rev").getAsString());
        user.setName(jsonDoc.get("name").getAsString());
        user.setRoles("");
        user.setUserAdmin(jsonDoc.get("isUserAdmin").getAsBoolean());
        user.setJoinDate(jsonDoc.get("joinDate").getAsInt());
        user.setFirstName(jsonDoc.get("firstName").getAsString());
        user.setLastName(jsonDoc.get("lastName").getAsString());
        user.setMiddleName(jsonDoc.get("middleName").getAsString());
        user.setEmail(jsonDoc.get("email").getAsString());
        user.setPhoneNumber(jsonDoc.get("phoneNumber").getAsString());
        user.setPassword_scheme(jsonDoc.get("password_scheme").getAsString());
        user.setIterations(jsonDoc.get("iterations").getAsString());
        user.setDerived_key(jsonDoc.get("derived_key").getAsString());
        user.setSalt(jsonDoc.get("salt").getAsString());
        user.setDob(jsonDoc.get("birthDate") == null ? "" : jsonDoc.get("birthDate").getAsString());
        user.setCommunityName(jsonDoc.get("communityName") == null ? "" : jsonDoc.get("communityName").getAsString());
        user.addImageUrl(jsonDoc, settings);
    }


    private void populateShelfItems(SharedPreferences settings, Realm mRealm) {
        properties.setDbName("shelf");
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
        CouchDbClientAndroid dbShelfClient = new CouchDbClientAndroid(properties);
        try {
            this.mRealm = mRealm;
            JsonObject jsonDoc = dbShelfClient.find(JsonObject.class, shelfDoc.getId());
            Utilities.log("Json Doc " + jsonDoc.toString());
            if (jsonDoc.getAsJsonArray("resourceIds") != null) {
                JsonArray array_resourceIds = jsonDoc.getAsJsonArray("resourceIds");
                JsonArray array_meetupIds = jsonDoc.getAsJsonArray("meetupIds");
                JsonArray array_courseIds = jsonDoc.getAsJsonArray("courseIds");
                JsonArray array_myTeamIds = jsonDoc.getAsJsonArray("myTeamIds");
                memberShelfData(array_resourceIds, array_meetupIds, array_courseIds, array_myTeamIds);
            } else {
                Log.e("DB", " BAD Metadata -- Shelf Doc ID " + shelfDoc.getId());
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void memberShelfData(JsonArray array_resourceIds, JsonArray array_meetupIds, JsonArray array_courseIds, JsonArray array_myTeamIds) {
        setVariables(settings, mRealm, properties);
        if (array_resourceIds.size() > 0) {
            RealmResults<realm_myLibrary> category = null;
            triggerInsert("resourceId", "resources");
            check(stringArray, array_resourceIds, realm_myLibrary.class, category);
        }
        if (array_meetupIds.size() > 0) {
            triggerInsert("meetupId", "meetups");
            RealmResults<realm_meetups> category = null;
            check(stringArray, array_meetupIds, realm_meetups.class, category);
        }
        if (0 < array_courseIds.size()) {
            RealmResults<realm_myCourses> category = null;
            triggerInsert("courseId", "courses");
            check(stringArray, array_courseIds, realm_myCourses.class, category);
        }
        if (array_myTeamIds.size() > 0) {
            checkMyTeams(shelfDoc.getId(), array_myTeamIds);
        }
    }

    private void triggerInsert(String categroryId, String categoryDBName) {
        stringArray[0] = shelfDoc.getId();
        stringArray[1] = categroryId;
        stringArray[2] = categoryDBName;
    }


    private void check(String[] stringArray, JsonArray array_categoryIds, Class aClass, RealmResults<?> db_Categrory) {
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

    private void triggerInsert(String[] stringArray, JsonArray array_categoryIds, int x, JsonObject resourceDoc) {
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

    private void checkMyTeams(String userId, JsonArray array_myTeamIds) {
        for (int tms = 0; tms < array_myTeamIds.size(); tms++) {
        }
    }


    private void setRealmProperties(String dbName) {
        properties.setDbName(dbName);
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
    }

    private void insertMyLibrary(String userId, String resourceID, JsonObject resourceDoc) {
        realm_myLibrary myLibraryDB = mRealm.createObject(realm_myLibrary.class, UUID.randomUUID().toString());
        myLibraryDB.setUserId(userId);
        myLibraryDB.setResourceId(resourceID);
        myLibraryDB.setResource_rev(resourceDoc.get("_rev").getAsString());
        myLibraryDB.setTitle(resourceDoc.get("title").getAsString());
        myLibraryDB.setAuthor(resourceDoc.get("author").getAsString());
//        myLibraryDB.setPublisher(resourceDoc.get("Publisher").getAsString());
//        myLibraryDB.setMedium(resourceDoc.get("medium").getAsString());
        myLibraryDB.setLanguage(resourceDoc.get("language").isJsonArray() ? resourceDoc.get("language").getAsJsonArray().toString() : resourceDoc.get("language").getAsString()); //array
        myLibraryDB.setSubject(resourceDoc.get("subject").isJsonArray() ? resourceDoc.get("subject").getAsJsonArray().toString() : resourceDoc.get("subject").getAsString()); // array
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

    private void insertMyMeetups(String userId, String meetupID, JsonObject meetupDoc) {
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

    private void insertMyCourses(String userId, String myCoursesID, JsonObject myCousesDoc) {
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

    private void insertCourseSteps(String myCoursesID, JsonArray steps, int numberOfSteps) {
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

    private void insertCourseStepsExams(String myCoursesID, String step_id, JsonObject exam) {
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
            if (question.has("correctChoice") && question.get("type").getAsString().equals("select")) {
                insertChoices(questionId, question.get("choices").getAsJsonArray());
            } else {
                myQuestion.setChoice(question.get("choices").getAsJsonArray());
            }
        }
    }

    private void insertChoices(String questionId, JsonArray choices) {
        for (int i = 0; i < choices.size(); i++) {
            JsonObject res = choices.get(i).getAsJsonObject();
            realm_answerChoices.create(mRealm, questionId, res, settings);
            Utilities.log("Insert choice " + res);
        }
    }

    public void insertCourseStepsAttachments(String myCoursesID, String stepId, JsonArray resources) {
        for (int i = 0; i < resources.size(); i++) {
            JsonObject res = resources.get(i).getAsJsonObject();
            realm_stepResources.create(mRealm, res, myCoursesID, stepId, settings);
        }
    }

    public void insertMyTeams(realm_meetups myMyTeamsDB, String userId, String myTeamsID, JsonObject myTeamsDoc) {

    }

}
