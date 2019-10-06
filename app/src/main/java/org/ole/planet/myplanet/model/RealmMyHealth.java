package org.ole.planet.myplanet.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.utilities.JsonUtils;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RealmMyHealth extends RealmObject {
    @PrimaryKey
    private String id;
    private String firstName, middleName, lastName, email, phone, language, birthDate, birthPlace, emergency, contactType, contact, specialNeeds, otherNeeds;
    private String userId;
    private String _rev;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public void setBirthPlace(String birthPlace) {
        this.birthPlace = birthPlace;
    }

    public String getEmergency() {
        return emergency;
    }

    public void setEmergency(String emergency) {
        this.emergency = emergency;
    }

    public String getContactType() {
        return contactType;
    }

    public void setContactType(String contactType) {
        this.contactType = contactType;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getSpecialNeeds() {
        return specialNeeds;
    }

    public void setSpecialNeeds(String specialNeeds) {
        this.specialNeeds = specialNeeds;
    }

    public String getOtherNeeds() {
        return otherNeeds;
    }

    public void setOtherNeeds(String otherNeeds) {
        this.otherNeeds = otherNeeds;
    }


    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String get_rev() {
        return _rev;
    }

    public void set_rev(String _rev) {
        this._rev = _rev;
    }

    public JsonObject serialize(RealmMyHealth myHealth) {
        JsonObject object = new JsonObject();
        object.addProperty("_id", getId());
        object.addProperty("userId", getUserId());
        object.addProperty("_rev", get_rev());
        object.addProperty("firstName", getFirstName());
        object.addProperty("lastName", getLastName());
        object.addProperty("middleName", getMiddleName());
        object.addProperty("email", getEmail());
        object.addProperty("language", getLanguage());
        object.addProperty("phoneNumber", getPhone());
        object.addProperty("birthPlace", getBirthPlace());
        object.addProperty("birthDate", getBirthDate());
        object.addProperty("emergency", getEmergency());
        object.addProperty("contact", getContact());
        object.addProperty("contactType", getContactType());
        object.addProperty("specialNeeds", getSpecialNeeds());
        object.addProperty("otherNeeds", getOtherNeeds());
        if (myHealth.get_rev() != null) object.addProperty("_rev", myHealth.get_rev());
        return object;
    }

    public static void insert(Realm mRealm, JsonObject act) {
        RealmMyHealth myHealth = mRealm.where(RealmMyHealth.class).equalTo("_id", JsonUtils.getString("_id", act)).findFirst();
        if (myHealth == null)
            myHealth = mRealm.createObject(RealmMyHealth.class, JsonUtils.getString("_id", act));
        myHealth.setId(JsonUtils.getString("_id", act));
        myHealth.setUserId(JsonUtils.getString("userId", act));
        myHealth.set_rev(JsonUtils.getString("_rev", act));
        myHealth.setFirstName(JsonUtils.getString("firstName", act));
        myHealth.setLastName(JsonUtils.getString("lastName", act));
        myHealth.setMiddleName(JsonUtils.getString("middleName", act));
        myHealth.setLanguage(JsonUtils.getString("language", act));
        myHealth.setEmail(JsonUtils.getString("email", act));
        myHealth.setPhone(JsonUtils.getString("phoneNumber", act));
        myHealth.setBirthPlace(JsonUtils.getString("birthPlace",act));
        myHealth.setBirthDate(JsonUtils.getString("birthDate",act));
        myHealth.setEmergency(JsonUtils.getString("emergency",act));
        myHealth.setContact(JsonUtils.getString("contact",act));
        myHealth.setContactType(JsonUtils.getString("contactType",act));
        myHealth.setSpecialNeeds(JsonUtils.getString("specialNeeds",act));
        myHealth.setOtherNeeds(JsonUtils.getString("otherNeeds",act));
        myHealth.set_rev(JsonUtils.getString("_rev", act));
    }
}
