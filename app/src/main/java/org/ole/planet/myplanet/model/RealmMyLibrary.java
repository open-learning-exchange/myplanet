package org.ole.planet.myplanet.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class RealmMyLibrary extends RealmObject {
    @PrimaryKey
    private String id;
    private String _id;
    private RealmList<String> userId;
    private String resourceRemoteAddress;
    private String resourceLocalAddress;
    private Boolean resourceOffline = false;
    private String resourceId;
    private String _rev;
    private String downloadedRev;
    private boolean need_optimization;
    private String publisher;
    private String linkToLicense;
    private String addedBy;
    private String uploadDate;
    private long createdDate;
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
    private String resourceType;
    private String description;
    private String sendOnAccept;
    private String translationAudioPath;
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
    private boolean isPrivate;


    public static List<RealmMyLibrary> getMyLibraryByUserId(Realm mRealm, SharedPreferences settings) {
        RealmResults<RealmMyLibrary> libs = mRealm.where(RealmMyLibrary.class).findAll();
        return getMyLibraryByUserId(settings.getString("userId", "--"), libs, mRealm);
    }


    public static List<RealmMyLibrary> getMyLibraryByUserId(String userId, List<RealmMyLibrary> libs, Realm mRealm) {
        List<RealmMyLibrary> libraries = new ArrayList<>();
        List<String> ids = RealmMyTeam.getResourceIdsByUser(userId, mRealm);

        for (RealmMyLibrary item : libs) {
            if (item.getUserId().contains(userId) || ids.contains(item.getResourceId())) {
                libraries.add(item);
            }
        }
        return libraries;
    }


    public static List<RealmMyLibrary> getMyLibraryByUserId(String userId, List<RealmMyLibrary> libs) {
        List<RealmMyLibrary> libraries = new ArrayList<>();

        for (RealmMyLibrary item : libs) {
            if (item.getUserId().contains(userId)) {
                libraries.add(item);
            }
        }
        return libraries;
    }

    public static List<RealmMyLibrary> getOurLibrary(String userId, List<RealmMyLibrary> libs) {
        List<RealmMyLibrary> libraries = new ArrayList<>();
        for (RealmMyLibrary item : libs) {
            if (!item.getUserId().contains(userId)) {
                libraries.add(item);
            }
        }
        return libraries;
    }

//
//    private static boolean containsId(RealmObject item, Class c,String userId) {
//        if (c == RealmMyCourse.class){
//           return ((RealmMyCourse) item).getUserId().contains(userId);
//        }else if(c == RealmMyLibrary.class ){
//            return ((RealmMyLibrary) item).getUserId().contains(userId);
//        }else if(c == RealmMyTeam.class){
//            return ((RealmMyTeam)item).getUserId().contains(userId);
//        }
//        return false;
//    }

    public static String[] getIds(Realm mRealm) {
        List<RealmMyLibrary> list = mRealm.where(RealmMyLibrary.class).findAll();
        String[] ids = new String[list.size()];
        int i = 0;
        for (RealmMyLibrary library : list
        ) {
            ids[i] = (library.getResource_id());
            i++;
        }
        return ids;
    }

    public static void removeDeletedResource(List<String> newIds, Realm mRealm) {
        String[] ids = getIds(mRealm);
        for (String id : ids
        ) {
            if (!newIds.contains(id)) {
                mRealm.where(RealmMyLibrary.class).equalTo("resourceId", id).findAll().deleteAllFromRealm();
            }
        }
    }

    public static JsonObject serialize(RealmMyLibrary personal, RealmUserModel user) {
        JsonObject object = new JsonObject();
        object.addProperty("title", personal.getTitle());
        object.addProperty("uploadDate", new Date().getTime());
        object.addProperty("createdDate", personal.getCreatedDate());
        object.addProperty("filename", FileUtils.getFileNameFromUrl(personal.getResourceLocalAddress()));
        object.addProperty("author", user.getName());
        object.addProperty("addedBy", user.getId());
        object.addProperty("medium", personal.getMedium());
        object.addProperty("description", personal.getDescription());
        object.addProperty("year", personal.getYear());
        object.addProperty("language", personal.getLanguage());
        object.add("subject", JsonUtils.getAsJsonArray(personal.getSubject()));
        object.add("level", JsonUtils.getAsJsonArray(personal.getLevel()));
        object.addProperty("resourceType", personal.getResourceType());
        object.addProperty("openWith", personal.getOpenWith());
        object.add("resourceFor", JsonUtils.getAsJsonArray(personal.getResourceFor()));
        object.addProperty("private", false);
        object.addProperty("isDownloadable", "");
        object.addProperty("sourcePlanet", user.getPlanetCode());
        object.addProperty("resideOn", user.getPlanetCode());
        object.addProperty("updatedDate", Calendar.getInstance().getTimeInMillis());
        object.addProperty("createdDate", personal.getCreatedDate());
        object.addProperty("androidId", NetworkUtils.getMacAddr());
        object.addProperty("deviceName", NetworkUtils.getDeviceName());
        object.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context));
//        object.addProperty("mediaType", personal.getMediaType());
        return object;
    }


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
        insertMyLibrary(userId, "", "", doc, mRealm);
    }

    public String getMedium() {
        return medium;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public static void createFromResource(RealmMyLibrary resource, Realm mRealm, String userId) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        resource.setUserId(userId);
        mRealm.commitTransaction();
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public static void insertMyLibrary(String userId, String stepId, String courseId, JsonObject doc, Realm mRealm) {

        String resourceId = JsonUtils.getString("_id", doc);
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        RealmMyLibrary resource = mRealm.where(RealmMyLibrary.class).equalTo("id", resourceId).findFirst();
        if (resource == null) {
            resource = mRealm.createObject(RealmMyLibrary.class, resourceId);
        }
//        if (!TextUtils.isEmpty(userId) ) {
        resource.setUserId(userId);
        resource.set_id(resourceId);
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
        resource.setPublisher(JsonUtils.getString("publisher", doc));
        resource.setLinkToLicense(JsonUtils.getString("linkToLicense", doc));
        resource.setOpenWith(JsonUtils.getString("openWith", doc));
        resource.setArticleDate(JsonUtils.getString("articleDate", doc));
        resource.setKind(JsonUtils.getString("kind", doc));
        resource.setCreatedDate(JsonUtils.getLong("createdDate", doc));
        resource.setLanguage(JsonUtils.getString("language", doc));
        resource.setAuthor(JsonUtils.getString("author", doc));
        resource.setMediaType(JsonUtils.getString("mediaType", doc));
        resource.setResourceType(JsonUtils.getString("resourceType", doc));
        resource.setTimesRated(JsonUtils.getInt("timesRated", doc));
        resource.setMedium(JsonUtils.getString("medium", doc));
        resource.setResourceFor(JsonUtils.getJsonArray("resourceFor", doc), resource);
        resource.setSubject(JsonUtils.getJsonArray("subject", doc), resource);
        resource.setLevel(JsonUtils.getJsonArray("level", doc), resource);
        resource.setTag(JsonUtils.getJsonArray("tags", doc), resource);
        resource.setPrivate(JsonUtils.getBoolean("private", doc));
        resource.setLanguages(JsonUtils.getJsonArray("languages", doc), resource);
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public JsonObject serializeResource() {
        JsonObject object = new JsonObject();
        object.addProperty("_id", _id);
        object.addProperty("_rev", _rev);
        object.addProperty("_rev", _rev);
        object.addProperty("need_optimization", need_optimization);

        object.add("resourceFor", getArray(resourceFor));
        object.addProperty("publisher", publisher);
        object.addProperty("linkToLicense", linkToLicense);
        object.addProperty("addedBy", addedBy);
        object.addProperty("uploadDate", uploadDate);
        object.addProperty("openWith", openWith);
        object.add("subject", getArray(subject));
        object.addProperty("kind", kind);
        object.addProperty("medium", medium);
        object.addProperty("language", language);
        object.addProperty("author", author);
        object.addProperty("sum", sum);
        object.addProperty("createdDate", uploadDate);
        object.add("level", getArray(level));
        object.add("languages", getArray(languages));
        object.add("tag", getArray(tag));
        object.addProperty("timesRated", timesRated);
        object.addProperty("year", year);
        object.addProperty("title", title);
        object.addProperty("averageRating", averageRating);
        object.addProperty("filename", filename);
        object.addProperty("mediaType", mediaType);
        object.addProperty("description", description);
        JsonObject ob = new JsonObject();
        ob.add(resourceLocalAddress, new JsonObject());
        object.add("_attachments", ob);
        return object;
    }

    public JsonArray getArray(RealmList<String> ar) {
        JsonArray sub = new JsonArray();
        if (ar != null) {
            for (String s : ar
            ) {
                sub.add(s);
            }
        }
        return sub;
    }

    public RealmList<String> getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        if (this.userId == null) {
            this.userId = new RealmList<>();
        }

        if (!this.userId.contains(userId) && !TextUtils.isEmpty(userId))
            this.userId.add(userId);
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
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

    public Boolean isResourceOffline() {
        return resourceOffline && TextUtils.equals(_rev, downloadedRev);
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


    public void setResourceFor(JsonArray array, RealmMyLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getResourceFor().contains(s.getAsString()))
                resource.getResourceFor().add(s.getAsString());
        }
    }

    public void setSubject(JsonArray array, RealmMyLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getSubject().contains(s.getAsString()))
                resource.getSubject().add(s.getAsString());
        }
    }

    public void setLevel(JsonArray array, RealmMyLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getLevel().contains(s.getAsString()))
                resource.getLevel().add(s.getAsString());
        }
    }

    public String getTranslationAudioPath() {
        return translationAudioPath;
    }

    public void setTranslationAudioPath(String translationAudioPath) {
        this.translationAudioPath = translationAudioPath;
    }

    public void setTag(JsonArray array, RealmMyLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getTag().contains(s.getAsString()))
                resource.getTag().add(s.getAsString());
        }
    }

    public void setLanguages(JsonArray array, RealmMyLibrary resource) {
        for (JsonElement s :
                array) {
            if (!(s instanceof JsonNull) && !resource.getLanguages().contains(s.getAsString()))
                resource.getLanguages().add(s.getAsString());
        }
    }


    public static CharSequence[] getListAsArray(RealmResults<RealmMyLibrary> db_myLibrary) {
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

    public void setUserId(RealmList<String> userId) {
        this.userId = userId;
    }

    public String getDownloadedRev() {
        return downloadedRev;
    }

    public void setDownloadedRev(String downloadedRev) {
        this.downloadedRev = downloadedRev;
    }

    public String getSubjectsAsString() {
        if ((subject).isEmpty()) {
            return "";
        }
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
        return publisher;
    }

    public void setPublisher(String publisher) {
        publisher = publisher;
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

    public static List<String> save(JsonArray allDocs, Realm mRealm) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < allDocs.size(); i++) {
            JsonObject doc = allDocs.get(i).getAsJsonObject();
            doc = JsonUtils.getJsonObject("doc", doc);
            String id = JsonUtils.getString("_id", doc);
            if (!id.startsWith("_design")) {
                list.add(id);
                RealmMyLibrary.insertResources(doc, mRealm);
            }
        }
        return list;
    }

    public static JsonArray getMyLibIds(Realm realm, String userId) {
        List<RealmMyLibrary> myLibraries = realm.where(RealmMyLibrary.class).contains("userId", userId).findAll();
        JsonArray ids = new JsonArray();
        for (RealmMyLibrary lib : myLibraries
        ) {
            ids.add(lib.getId());
        }
        return ids;
    }

    @Override
    public String toString() {
        return title;
    }

    public void removeUserId(String id) {
        this.userId.remove(id);
    }

    public boolean needToUpdate() {
        return (getResourceLocalAddress() != null) && !getResourceOffline() || !(TextUtils.equals(get_rev(), getDownloadedRev()));
    }


//    public static Set<String> getLanguages(List<RealmMyLibrary> libraries) {
//        Set<String> list = new HashSet<>();
//        for (RealmMyLibrary li : libraries) {
//            if (!TextUtils.isEmpty(li.getLanguage()))
//                list.add(li.getLanguage());
//        }
//        return list;
//    }

    public static Set<String> getLevels(List<RealmMyLibrary> libraries) {
        Set<String> list = new HashSet<>();
        for (RealmMyLibrary li : libraries) {
            list.addAll(li.getLevel());
        }
        return list;
    }

    public static Set<String> getArrayList(List<RealmMyLibrary> libraries, String type) {
        Set<String> list = new HashSet<>();
        for (RealmMyLibrary li : libraries) {
            String s = type.equals("mediums") ? li.getMediaType() : li.getLanguage();
            if (!TextUtils.isEmpty(s))
                list.add(s);
        }
        return list;
    }

    public static Set<String> getSubjects(List<RealmMyLibrary> libraries) {
        Set<String> list = new HashSet<>();
        for (RealmMyLibrary li : libraries) {
            list.addAll(li.getSubject());
        }
        return list;
    }


}