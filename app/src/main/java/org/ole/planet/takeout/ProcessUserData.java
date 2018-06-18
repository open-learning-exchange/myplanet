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
import org.ole.planet.takeout.Data.realm_myLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

public abstract class ProcessUserData extends AppCompatActivity {
    SharedPreferences settings;
    Realm mRealm;
    CouchDbProperties properties;
    CouchDbClientAndroid dbResources;

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
            checkMyLibrary(doc.getId(), array_resourceIds);
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

    public void checkMyMeetups(JsonArray array_resourceIds) {
    }

    public void checkMyCourses(JsonArray array_resourceIds) {
    }

    public void checkMyTeams(JsonArray array_resourceIds) {
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


}
