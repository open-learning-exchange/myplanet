package org.ole.planet.takeout;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;

import com.google.gson.JsonObject;

import io.realm.Realm;

public abstract class ProcessUserData extends AppCompatActivity {

    public static final String PREFS_NAME = "OLE_PLANET";
    SharedPreferences settings;

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

    public void populateUsersTable(JsonObject jsonDoc, Realm mRealm) {
        try {
            realm_UserModel user = mRealm.createObject(realm_UserModel.class, jsonDoc.get("_id").getAsString());
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
            mRealm.commitTransaction();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

}
