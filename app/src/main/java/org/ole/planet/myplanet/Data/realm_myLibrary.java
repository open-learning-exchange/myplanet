package org.ole.planet.myplanet.Data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.kittinunf.fuel.android.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONStringer;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class realm_myLibrary extends RealmObject {
    @PrimaryKey
    private String id;
    private String userId;
    private String resourceRemoteAddress;
    private String resourceLocalAddress;
    private Boolean resourceOffline = false;
    private String resourceId;
    private String _rev;
    private boolean need_optimization;
    private String Publisher;
    private String linkToLicense;
    private String addedBy;
    private String uploadDate;
    private String openWith;
    private String articleDate;
    private String kind;
    private String language;
    private String author;
    private String year;
    private String medium;
    private String title;
    private String averageRating;
    private String filename;
    private String mediaType;
    private String description;
    private String sendOnAccept;
    private int sum;
    private int timesRated;
    private RealmList<String> resourceFor;
    private RealmList<String> subject;
    private RealmList<String> level;
    private RealmList<String> tag;
    private RealmList<String> languages;
    private String courseId;
    private String stepId;
    private String downloaded;


    public String getDownloaded() {
        return downloaded;
    }

    public void setDownloaded(String downloaded) {
        this.downloaded = downloaded;
    }

    public static void insertResources(JsonObject doc, Realm mRealm) {
        insertMyLibrary("", doc, mRealm);
    }


    public static void createStepResource(Realm mRealm, JsonObject res, String myCoursesID, String stepId) {
        insertMyLibrary("", stepId, myCoursesID, res, mRealm);

    }

    public static void insertMyLibrary(String userId, JsonObject doc, Realm mRealm) {
        Utilities.log("Insert shelf " + userId);
        insertMyLibrary(userId, "", "", doc, mRealm);
    }

    public String getMedium() {
        return medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public static void createFromResource(realm_myLibrary resource, Realm mRealm, String userId) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        resource.setUserId(userId);
        mRealm.commitTransaction();
    }


    public static void insertMyLibrary(String userId, String stepId, String courseId, JsonObject doc, Realm mRealm) {

        String resourceId = JsonUtils.getString("_id", doc);
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        realm_myLibrary resource = mRealm.where(realm_myLibrary.class).equalTo("id", resourceId).findFirst();
        if (resource == null) {
            resource = mRealm.createObject(realm_myLibrary.class, resourceId);
        }
//        if (!TextUtils.isEmpty(userId) ) {
        resource.setUserId(userId);
//        }
        if (!TextUtils.isEmpty(stepId)) {
            resource.setStepId(stepId);
        }
        if (!TextUtils.isEmpty(courseId)) {
            resource.setCourseId(courseId);
        }
        resource.set_rev(JsonUtils.getString("_rev", doc));

        resource.setResource_id(resourceId);
        resource.setTitle(JsonUtils.getString("title", doc));
        resource.setDescription(JsonUtils.getString("description", doc));
        if (doc.has("_attachments")) {
            JsonObject attachments = doc.get("_attachments").getAsJsonObject();
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(String.valueOf(attachments));
            JsonObject obj = element.getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> entries = obj.entrySet();
            for (Map.Entry<String, JsonElement> entry : entries) {
                if (entry.getKey().indexOf("/") < 0) {
                    resource.setResourceRemoteAddress(settings.getString("couchdbURL", "http://") + "/resources/" + resourceId + "/" + entry.getKey());
                    resource.setResourceLocalAddress(entry.getKey());
                    resource.setResourceOffline(FileUtils.checkFileExist(resource.getResourceRemoteAddress()));
                }
            }
        }
        resource.setFilename(JsonUtils.getString("filename", doc));
        resource.setAverageRating(JsonUtils.getString("averageRating", doc));
        resource.setUploadDate(JsonUtils.getString("uploadDate", doc));
        resource.setYear(JsonUtils.getString("year", doc));
        resource.setAddedBy(JsonUtils.getString("addedBy", doc));
        resource.setPublisher(JsonUtils.getString("Publisher", doc));
        resource.setLinkToLicense(JsonUtils.getString("linkToLicense", doc));
        resource.setOpenWith(JsonUtils.getString("openWith", doc));
        resource.setArticleDate(JsonUtils.getString("articleDate", doc));
        resource.setKind(JsonUtils.getString("kind", doc));
        resource.setLanguage(JsonUtils.getString("language", doc));
        resource.setAuthor(JsonUtils.getString("author", doc));
        resource.setMediaType(JsonUtils.getString("mediaType", doc));
        resource.setTimesRated(JsonUtils.getInt("timesRated", doc));
        resource.setMedium(JsonUtils.getString("medium", doc));
        resource.setResourceFor(JsonUtils.getJsonArray("resourceFor", doc), resource);
        resource.setSubject(JsonUtils.getJsonArray("subject", doc), resource);
        resource.setLevel(JsonUtils.getJsonArray("level", doc), resource);
        resource.setTag(JsonUtils.getJsonArray("tags", doc), resource);
        resource.setLanguages(JsonUtils.getJsonArray("languages", doc), resource);
    }


    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getResource_id() {
        return resourceId;
    }

    public void setResource_id(String resource_id) {
        this.resourceId = resource_id;
    }

    public RealmList<String> getResourceFor() {
        return resourceFor;
    }


    public void setResourceFor(JsonArray array, realm_myLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getResourceFor().contains(s.getAsString()))
                resource.getResourceFor().add(s.getAsString());
        }
    }

    public void setSubject(JsonArray array, realm_myLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getSubject().contains(s.getAsString()))
                resource.getSubject().add(s.getAsString());
        }
    }

    public void setLevel(JsonArray array, realm_myLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getLevel().contains(s.getAsString()))
                resource.getLevel().add(s.getAsString());
        }
    }

    public void setTag(JsonArray array, realm_myLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getTag().contains(s.getAsString()))
                resource.getTag().add(s.getAsString());
        }
    }

    public void setLanguages(JsonArray array, realm_myLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getLanguages().contains(s.getAsString()))
                resource.getLanguages().add(s.getAsString());
        }
    }


    public static CharSequence[] getListAsArray(RealmResults<realm_myLibrary> db_myLibrary) {
        CharSequence[] array = new CharSequence[db_myLibrary.size()];
        for (int i = 0; i < db_myLibrary.size(); i++) {
            array[i] = db_myLibrary.get(i).getTitle();
        }
        return array;
    }

    public void setResourceFor(RealmList<String> resourceFor) {
        this.resourceFor = resourceFor;
    }

    public RealmList<String> getSubject() {
        return subject;
    }


    public String getSubjectsAsString() {
        String str = "";
        for (String s : subject) {
            str += s + ", ";
        }
        return str.substring(0, str.length() - 1);
    }

    public void setSubject(RealmList<String> subject) {
        this.subject = subject;
    }

    public RealmList<String> getLevel() {
        return level;
    }

    public void setLevel(RealmList<String> level) {
        this.level = level;
    }

    public RealmList<String> getTag() {
        return tag;
    }
//
//    public String getTagAsString() {
//        StringBuilder s = new StringBuilder();
//        String[] tags = getTag().toArray(new String[getTag().size()]);
//        Arrays.sort(tags);
//        for (String tag : tags) {
//            s.append(tag).append(", ");
//        }
//        return s.toString();
//    }

    public static String listToString(RealmList<String> list) {
        StringBuilder s = new StringBuilder();
        for (String tag : list) {
            s.append(tag).append(", ");
        }
        return s.toString();

    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public void setTag(RealmList<String> tag) {
        this.tag = tag;
    }

    public RealmList<String> getLanguages() {
        return languages;
    }

    public void setLanguages(RealmList<String> languages) {
        this.languages = languages;
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

    public boolean isNeed_optimization() {
        return need_optimization;
    }

    public void setNeed_optimization(boolean need_optimization) {
        this.need_optimization = need_optimization;
    }

    public String getPublisher() {
        return Publisher;
    }

    public void setPublisher(String publisher) {
        Publisher = publisher;
    }

    public String getLinkToLicense() {
        return linkToLicense;
    }

    public void setLinkToLicense(String linkToLicense) {
        this.linkToLicense = linkToLicense;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public String getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(String uploadDate) {
        this.uploadDate = uploadDate;
    }

    public String getOpenWith() {
        return openWith;
    }

    public void setOpenWith(String openWith) {
        this.openWith = openWith;
    }

    public String getArticleDate() {
        return articleDate;
    }

    public void setArticleDate(String articleDate) {
        this.articleDate = articleDate;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(String averageRating) {
        this.averageRating = averageRating;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSendOnAccept() {
        return sendOnAccept;
    }

    public void setSendOnAccept(String sendOnAccept) {
        this.sendOnAccept = sendOnAccept;
    }

    public int getSum() {
        return sum;
    }

    public void setSum(int sum) {
        this.sum = sum;
    }

    public int getTimesRated() {
        return timesRated;
    }

    public void setTimesRated(int timesRated) {
        this.timesRated = timesRated;
    }

    public static void save(JsonArray allDocs, Realm mRealm) {
        for (int i = 0; i < allDocs.size(); i++) {
            realm_myLibrary.insertResources(allDocs.get(i).getAsJsonObject(), mRealm);
        }
    }

    public static JsonArray getMyLibIds(Realm realm, SharedPreferences sharedPreferences) {
        RealmResults<realm_myLibrary> myLibraries = realm.where(realm_myLibrary.class).isNotEmpty("userId")
                .equalTo("userId", sharedPreferences.getString("userId", "--"), Case.INSENSITIVE).findAll();

        JsonArray ids = new JsonArray();
        for (realm_myLibrary lib : myLibraries
                ) {
            ids.add(lib.getId());
        }
        return ids;
    }

    @Override
    public String toString() {
        return title;
    }
}