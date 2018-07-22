package org.ole.planet.takeout.Data;

import android.content.SharedPreferences;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.takeout.utilities.Utilities;

import java.util.Map;
import java.util.Set;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_UserModel extends RealmObject {
    @PrimaryKey
    private String id;
    private String _rev;
    private String name;
    private String roles;
    private Boolean isUserAdmin;
    private int joinDate;
    private String firstName;
    private String lastName;
    private String middleName;
    private String email;
    private String phoneNumber;
    private String password_scheme;
    private String iterations;
    private String derived_key;
    private String salt;
    private String  dob;
    private String communityName;
    private String userImage;

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

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
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
}
