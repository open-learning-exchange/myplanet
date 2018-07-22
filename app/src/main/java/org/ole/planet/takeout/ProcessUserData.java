package org.ole.planet.takeout;

import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.lightcouch.CouchDbClientAndroid;
import org.lightcouch.CouchDbProperties;
import org.lightcouch.Document;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_courseSteps;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmModel;
import io.realm.RealmObject;
import io.realm.RealmResults;

public abstract class ProcessUserData extends CustomDataProcessing {
    SharedPreferences settings;
    Realm mRealm;
    CouchDbProperties properties;
    CouchDbClientAndroid dbResources, dbMeetup, dbMyCourses;
    MaterialDialog progress_dialog;
    Document shelfDoc;
    String[] stringArray = new String[3];

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
        editor.putString("userId", user.getId());
        editor.putString("name", user.getName());
        editor.putString("password", password);
        editor.putString("firstName", user.getFirstName());
        editor.putString("lastName", user.getLastName());
        editor.putString("middleName", user.getMiddleName());
        editor.putBoolean("isUserAdmin", user.getUserAdmin());
        editor.commit();
    }

    public void userTransactionSync(SharedPreferences sett, Realm realm, CouchDbProperties propts, MaterialDialog progress_dialog) {
        this.progress_dialog = progress_dialog;
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

    public void myLibraryTransactionSync() {
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
                progress_dialog.dismiss();
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
        user.setDob(jsonDoc.get("dob") == null ? "" : jsonDoc.get("dob").getAsString());
        user.setCommunityName(jsonDoc.get("communityName") == null ? "" : jsonDoc.get("communityName").getAsString());
    }


    public void populateShelfItems(SharedPreferences settings, Realm mRealm) {
        properties.setDbName("shelf");
        properties.setUsername(settings.getString("url_user", ""));
        properties.setPassword(settings.getString("url_pwd", ""));
        CouchDbClientAndroid dbShelfClient = new CouchDbClientAndroid(properties);
        try {
            this.mRealm = mRealm;
            JsonObject jsonDoc = dbShelfClient.find(JsonObject.class, shelfDoc.getId());
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

    public void memberShelfData(JsonArray array_resourceIds, JsonArray array_meetupIds, JsonArray array_courseIds, JsonArray array_myTeamIds) {
        setVariables(settings, mRealm, properties);
        if (array_resourceIds.size() > 0) {
            RealmResults<realm_myLibrary> category = null;
            triggerInsert("resourceId", "resources");
            check(stringArray, array_resourceIds, realm_myLibrary.class, category);
        }
        if (array_meetupIds.size() > 0) {
            triggerInsert("meetupId", "meetups");
            RealmResults<realm_meetups> category = null;
            check(stringArray, array_resourceIds, realm_meetups.class, category);
        }
        if (0 < array_courseIds.size()) {
            RealmResults<realm_myCourses> category = null;
            triggerInsert("courseId", "courses");
            check(stringArray, array_resourceIds, realm_myCourses.class, category);
        }
        if (array_myTeamIds.size() > 0) {
            checkMyTeams(shelfDoc.getId(), array_myTeamIds);
        }
    }

    public void triggerInsert(String categroryId, String categoryDBName) {
        stringArray[0] = shelfDoc.getId();
        stringArray[1] = categroryId;
        stringArray[2] = categoryDBName;
    }
}
