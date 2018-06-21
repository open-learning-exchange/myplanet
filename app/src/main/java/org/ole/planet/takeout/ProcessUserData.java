package org.ole.planet.takeout;

import android.content.SharedPreferences;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myLibrary;

import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmModel;
import io.realm.RealmResults;

public abstract class ProcessUserData extends AppCompatActivity {
    SharedPreferences settings;
    Realm mRealm;
    CouchDbProperties properties;
    CouchDbClientAndroid dbResources;
    CouchDbClientAndroid dbMeetup;

    public boolean validateEditText(EditText textField, TextInputLayout textLayout, String err_message) {
        if (textField.getText().toString().trim().isEmpty()) {
            textLayout.setError(err_message);
            requestFocus(textField);
            return false;
        } else {
            textLayout.setErrorEnabled(false);
        }
        return true;
    }


    private void requestFocus(View view) {
        if (view.requestFocus()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    public void saveUserInfoPref(SharedPreferences settings, String password, realm_UserModel user) {
        this.settings = settings;
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("name", user.getName());
        editor.putString("password", password);
        editor.putString("firstName", user.getFirstName());
        editor.putString("lastName", user.getLastName());
        editor.putString("middleName", user.getMiddleName());
        editor.putBoolean("isUserAdmin", user.getUserAdmin());
        editor.commit();
    }

    public void userTransactionSync(SharedPreferences sett, Realm realm, CouchDbProperties propts) {
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

    public void myLybraryTransactionSync() {
        properties.setDbName("shelf");
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                CouchDbClientAndroid dbShelfClient = new CouchDbClientAndroid(properties);
                List<Document> allShelfDocs = dbShelfClient.view("_all_docs").includeDocs(true).query(Document.class);
                for (int i = 0; i < allShelfDocs.size(); i++) {
                    Document doc = allShelfDocs.get(i);
                    populateShelfItems(settings, doc, realm, properties);
                }
            }
        });
    }


    public void populateUsersTable(JsonObject jsonDoc, Realm mRealm) {
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

    public void insertIntoUsers(JsonObject jsonDoc, realm_UserModel user) {
        user.set_rev(jsonDoc.get("_rev").getAsString());
        user.setName(jsonDoc.get("name").getAsString());
        //JsonElement userRoles = jsonDoc.get("roles");
        //user.setRoles(userRolesAsJsonArray.getAsString());
        user.setRoles("");
        if ((jsonDoc.get("isUserAdmin").getAsString().equalsIgnoreCase("true"))) {
            user.setUserAdmin(true);
        } else {
            user.setUserAdmin(false);
        }
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
    }


    public void populateShelfItems(SharedPreferences settings, Document doc, Realm mRealm, CouchDbProperties properties) {
        CouchDbClientAndroid dbShelfClient = new CouchDbClientAndroid(properties);
        try {
            this.mRealm = mRealm;
            JsonObject jsonDoc = dbShelfClient.find(JsonObject.class, doc.getId());
            JsonArray array_resourceIds = jsonDoc.getAsJsonArray("resourceIds");
            JsonArray array_meetupIds = jsonDoc.getAsJsonArray("meetupIds");
            JsonArray array_courseIds = jsonDoc.getAsJsonArray("courseIds");
            JsonArray array_myTeamIds = jsonDoc.getAsJsonArray("myTeamIds");
            if(array_resourceIds!=null){
                checkMyLibrary(doc.getId(), array_resourceIds);
            }
            if(array_meetupIds!=null){
                checkMyMeetups(doc.getId(), array_meetupIds);
            }
            if(array_courseIds!=null){
                checkMyCourses(doc.getId(), array_courseIds);
            }
            if(array_myTeamIds!=null){
                checkMyTeams(doc.getId(), array_myTeamIds);
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void checkMyLibrary(String userId, JsonArray array_resourceIds) {
        for (int x = 0; x < array_resourceIds.size(); x++) {
            RealmResults<realm_myLibrary> db_myLibrary = mRealm.where(realm_myLibrary.class)
                    .equalTo("id", userId)
                    .equalTo("resourceId", array_resourceIds.get(x).getAsString())
                    .findAll();
            if (db_myLibrary.isEmpty()) {
                realm_myLibrary myLibraryDB = mRealm.createObject(realm_myLibrary.class, UUID.randomUUID().toString());
                properties.setDbName("resources");
                properties.setUsername(settings.getString("url_user", ""));
                properties.setPassword(settings.getString("url_pwd", ""));
                dbResources = new CouchDbClientAndroid(properties);
                JsonObject resourceDoc = dbResources.find(JsonObject.class, array_resourceIds.get(x).getAsString());
                insertMyLibrary(myLibraryDB, userId, array_resourceIds.get(x).getAsString(), resourceDoc);
            }

        }
    }

    public void checkMyMeetups(String userId,JsonArray array_meetupIds) {
        for (int x = 0; x < array_meetupIds.size(); x++) {
            RealmResults<realm_meetups> db_myMeetups = mRealm.where(realm_meetups.class)
                    .equalTo("id", userId)
                    .equalTo("meetupId", array_meetupIds.get(x).getAsString())
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
/*
//Todo merge the above similar code into one method
    public void checkCategory(String userId, String categoryId, Class<RealmModel> categoryClass){
        RealmResults<RealmModel> db_categoryId = mRealm.where(categoryClass)
                    .equalTo("id", userId)
                    .equalTo("meetupId", categoryId)
                    .findAll();
            if (db_categoryId.isEmpty()) {
                RealmModel myMeetupsDB = mRealm.createObject(categoryClass, UUID.randomUUID().toString());
                properties.setDbName("meetups");
                properties.setUsername(settings.getString("url_user", ""));
                properties.setPassword(settings.getString("url_pwd", ""));
                dbMeetup = new CouchDbClientAndroid(properties);
                JsonObject meetupDoc = dbMeetup.find(JsonObject.class, categoryId);
                insertMyMeetups(myMeetupsDB, userId, categoryId, meetupDoc);
            }

    }
    */

    public void checkMyCourses(String userId,JsonArray array_courseIds) {
        for (int x = 0; x < array_courseIds.size(); x++) {
            RealmResults<realm_meetups> db_myCourses = mRealm.where(realm_meetups.class)
                    .equalTo("userId", userId)
                    .equalTo("courseId", array_courseIds.get(x).getAsString())
                    .findAll();
            if (db_myCourses.isEmpty()) {

            }

        }
    }

    public void checkMyTeams(String userId,JsonArray array_myTeamIds) {
    }

    public void insertMyLibrary(realm_myLibrary myLibraryDB, String userId, String resourceID, JsonObject resourceDoc) {
        Log.e("myLibrary", resourceDoc.toString());
        myLibraryDB.setUserId(userId);
        myLibraryDB.setResourceId(resourceID);
        myLibraryDB.setResource_rev(resourceDoc.get("_rev").getAsString());
        myLibraryDB.setTitle(resourceDoc.get("title").getAsString());
        myLibraryDB.setAuthor(resourceDoc.get("author").getAsString());
        myLibraryDB.setPublisher(resourceDoc.get("Publisher").getAsString());
        myLibraryDB.setMedium(resourceDoc.get("medium").getAsString());
        myLibraryDB.setLanguage(resourceDoc.get("language").getAsString()); //array
        myLibraryDB.setSubject(resourceDoc.get("subject").getAsString()); // array
        myLibraryDB.setLinkToLicense(resourceDoc.get("linkToLicense").getAsString());
        myLibraryDB.setResourceFor(resourceDoc.get("resourceFor").getAsString());
        myLibraryDB.setMediaType(resourceDoc.get("mediaType").getAsString());
        myLibraryDB.setAverageRating(resourceDoc.get("averageRating").getAsString());
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
    public void insertMyCourses(realm_meetups myMyCoursesDB, String userId, String myCoursesID, JsonObject myCousesDoc) {

    }
    public void insertMyTeams(realm_meetups myMyTeamsDB, String userId, String myTeamsID, JsonObject myTeamsDoc) {

    }



}
