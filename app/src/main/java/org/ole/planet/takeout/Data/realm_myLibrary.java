package org.ole.planet.takeout.Data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.takeout.MainApplication;
import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class realm_myLibrary extends RealmObject {
    @PrimaryKey
    private String id;
    private String userId;
    private String resourceId;
    private String resource_rev;
    private String title;
    private String author;
    private String publisher;
    private String medium;
    private String language;
    private String subject;
    private String linkToLicense;
    private String resourceFor;
    private String mediaType;
    private String averageRating;
    private String description;
    private String resourceRemoteAddress;
    private String resourceLocalAddress;
    private Boolean resourceOffline;
    private int fileLength;

    public static CharSequence[] getListAsArray(RealmResults<realm_myLibrary> db_myLibrary) {
        CharSequence[] array = new CharSequence[db_myLibrary.size()];
        for (int i = 0; i < db_myLibrary.size(); i++) {
            array[i] = db_myLibrary.get(i).getTitle();
        }
        return array;
    }

    public static void insertMyLibrary(String userId, String resourceID, JsonObject resourceDoc, Realm mRealm, SharedPreferences settings) {
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

    public static void createFromResource(realm_resources resource, Realm mRealm, String userId) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        SharedPreferences preferences = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        realm_myLibrary myLibraryDB = mRealm.createObject(realm_myLibrary.class, UUID.randomUUID().toString());
        myLibraryDB.setUserId(userId);
        myLibraryDB.setResourceId(resource.getResource_id());
        myLibraryDB.setResource_rev(resource.get_rev());
        myLibraryDB.setTitle(resource.getTitle());
        myLibraryDB.setAuthor(resource.getAuthor());
        myLibraryDB.setLanguage(resource.getLanguage());
        myLibraryDB.setSubject(resource.getSubject().isEmpty() ? "" : resource.getSubject().first()); // array
        myLibraryDB.setMediaType(resource.getMediaType());
        myLibraryDB.setResourceRemoteAddress(Utilities.getUrl(resource.getResource_id(), resource.getFilename(), preferences));
        myLibraryDB.setResourceLocalAddress(resource.getFilename());
        myLibraryDB.setDescription(resource.getDescription());
        myLibraryDB.setResourceOffline(false);
        mRealm.commitTransaction();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getResource_rev() {
        return resource_rev;
    }

    public void setResource_rev(String resource_rev) {
        this.resource_rev = resource_rev;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getMedium() {
        return medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getFileLength() {
        return fileLength;
    }

    public void setFileLength(int fileLength) {
        this.fileLength = fileLength;
    }

    public String getLinkToLicense() {
        return linkToLicense;
    }

    public void setLinkToLicense(String linkToLicense) {
        this.linkToLicense = linkToLicense;
    }

    public String getResourceFor() {
        return resourceFor;
    }

    public void setResourceFor(String resourceFor) {
        this.resourceFor = resourceFor;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(String averageRating) {
        this.averageRating = averageRating;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceRemoteAddress() {
        return resourceRemoteAddress;
    }

    public void setResourceRemoteAddress(String resourceRemoteAddress) {
        this.resourceRemoteAddress = resourceRemoteAddress;
    }

    public String getResourceLocalAddress() {
        return resourceLocalAddress;
    }

    public void setResourceLocalAddress(String resourceLocalAddress) {
        this.resourceLocalAddress = resourceLocalAddress;
    }

    public Boolean getResourceOffline() {
        return resourceOffline;
    }

    public void setResourceOffline(Boolean resourceOffline) {
        this.resourceOffline = resourceOffline;
    }
}