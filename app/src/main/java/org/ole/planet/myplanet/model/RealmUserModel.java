package org.ole.planet.myplanet.model;

import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.lang3.StringUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;
import org.picketbox.util.StringUtil;

import java.util.Map;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmUserModel extends RealmObject {
    @PrimaryKey
    private String id;
    private String _rev;
    private String name;
    private RealmList<String> roles;
    private Boolean isUserAdmin;
    private int joinDate;
    private String firstName;
    private String lastName;
    private String middleName;
    private String email;
    private String planetCode;
    private String parentCode;
    private String phoneNumber;
    private String password_scheme;
    private String iterations;
    private String derived_key;
    private String salt;
    private String dob;
    private String birthPlace;
    private String communityName;
    private String userImage;
    private boolean showTopbar;


    public JsonObject serialize() {
        JsonObject object = new JsonObject();
        object.addProperty("_id", getId());
        object.addProperty("_rev", get_rev());
        object.addProperty("name", getName());
        object.add("roles", getRoles());
        object.addProperty("isUserAdmin", getUserAdmin());
        object.addProperty("joinDate", getJoinDate());
        object.addProperty("firstName", getFirstName());
        object.addProperty("lastName", getLastName());
        object.addProperty("middleName", getMiddleName());
        object.addProperty("email", getEmail());
        object.addProperty("phoneNumber", getPhoneNumber());
        return object;
    }

    public static void populateUsersTable(JsonObject jsonDoc, Realm mRealm, SharedPreferences settings) {
        try {
            RealmUserModel user = mRealm.where(RealmUserModel.class).equalTo("id", JsonUtils.getString("_id", jsonDoc)).findFirst();
            if (user == null) {
                user = mRealm.createObject(RealmUserModel.class, JsonUtils.getString("_id", jsonDoc));
            }
            insertIntoUsers(jsonDoc, user, settings);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private static void insertIntoUsers(JsonObject jsonDoc, RealmUserModel user, SharedPreferences settings) {
        Utilities.log("Insert into users " + new Gson().toJson(jsonDoc));
        user.set_rev(JsonUtils.getString("_rev", jsonDoc));
        user.setName(JsonUtils.getString("name", jsonDoc));
        JsonArray array = JsonUtils.getJsonArray("roles", jsonDoc);
        RealmList<String> roles = new RealmList<>();
        for (int i = 0; i < array.size(); i++) {
            roles.add(JsonUtils.getString(array, i));
        }
        user.setRoles(roles);
        user.setUserAdmin(JsonUtils.getBoolean("isUserAdmin", jsonDoc));
        user.setJoinDate(JsonUtils.getInt("joinDate", jsonDoc));
        user.setFirstName(JsonUtils.getString("firstName", jsonDoc));
        user.setLastName(JsonUtils.getString("lastName", jsonDoc));
        user.setMiddleName(JsonUtils.getString("middleName", jsonDoc));
        user.setPlanetCode(JsonUtils.getString("planetCode", jsonDoc));
        user.setParentCode(JsonUtils.getString("parentCode", jsonDoc));
        user.setEmail(JsonUtils.getString("email", jsonDoc));
        user.setPhoneNumber(JsonUtils.getString("phoneNumber", jsonDoc));
        user.setPassword_scheme(JsonUtils.getString("password_scheme", jsonDoc));
        user.setIterations(JsonUtils.getString("iterations", jsonDoc));
        user.setDerived_key(JsonUtils.getString("derived_key", jsonDoc));
        user.setSalt(JsonUtils.getString("salt", jsonDoc));
        user.setDob(JsonUtils.getString("birthDate", jsonDoc));
        user.setBirthPlace(JsonUtils.getString("birthPlace", jsonDoc));
        user.setCommunityName(JsonUtils.getString("communityName", jsonDoc));
        user.setShowTopbar(true);
        user.addImageUrl(jsonDoc, settings);
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public void setBirthPlace(String birthPlace) {
        this.birthPlace = birthPlace;
    }

    public RealmList<String> getRolesList() {
        return roles;
    }

    public JsonArray getRoles() {
        JsonArray ar = new JsonArray();
        for (String s : roles
        ) {
            ar.add(s);
        }
        return ar;
    }

    public void setRoles(RealmList<String> roles) {
        this.roles = roles;
    }

    public String getRoleAsString() {
        String s = StringUtils.join(getRolesList(), ",");
        return s;
    }

    public boolean isShowTopbar() {
        return showTopbar;
    }

    public void setShowTopbar(boolean showTopbar) {
        this.showTopbar = showTopbar;
    }

    public String getPlanetCode() {
        return planetCode;
    }

    public void setPlanetCode(String planetCode) {
        this.planetCode = planetCode;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getUserImage() {
        return userImage;
    }

    public void setUserImage(String userImage) {
        this.userImage = userImage;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getCommunityName() {
        return communityName;
    }

    public void setCommunityName(String communityName) {
        this.communityName = communityName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }


    public Boolean getUserAdmin() {
        return isUserAdmin;
    }

    public void setUserAdmin(Boolean userAdmin) {
        isUserAdmin = userAdmin;
    }

    public int getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(int joinDate) {
        this.joinDate = joinDate;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPassword_scheme() {
        return password_scheme;
    }

    public void setPassword_scheme(String password_scheme) {
        this.password_scheme = password_scheme;
    }

    public String getIterations() {
        return iterations;
    }

    public void setIterations(String iterations) {
        this.iterations = iterations;
    }

    public String getDerived_key() {
        return derived_key;
    }

    public void setDerived_key(String derived_key) {
        this.derived_key = derived_key;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public void addImageUrl(JsonObject jsonDoc, SharedPreferences settings) {
        if (jsonDoc.has("_attachments")) {
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(String.valueOf(jsonDoc.get("_attachments").getAsJsonObject()));
            JsonObject obj = element.getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> entries = obj.entrySet();
            for (Map.Entry<String, JsonElement> entry : entries) {
                this.setUserImage(Utilities.getUserImageUrl(this.getId(), entry.getKey(), settings));
                break;
            }
        }
    }

    public Boolean getShowTopbar() {
        return showTopbar;
    }

    public void setShowTopbar(Boolean showTopbar) {
        this.showTopbar = showTopbar;
    }

    @Override
    public String toString() {
        return " - " + name;
    }

}
