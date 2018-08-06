package org.ole.planet.takeout.Data;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_resources extends RealmObject {
    @PrimaryKey
    private String id;
    private String resource_id;
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


    public static void insertResources(JsonObject doc, Realm mRealm) {
        realm_resources resource = mRealm.createObject(realm_resources.class, UUID.randomUUID().toString());
        resource.set_rev(doc.get("_rev").getAsString());
        resource.setResource_id(doc.get("_id").getAsString());
        resource.setTitle(doc.get("title").getAsString());
        resource.setDescription(doc.get("description").getAsString());
        if (doc.has("need_optimization")) {
            resource.setNeed_optimization(doc.get("need_optimization").getAsBoolean());
        }
        resource.setYear(doc.get("year").getAsString());
        resource.setAverageRating(doc.has("averageRating") ? doc.get("averageRating").getAsString() : "");
        resource.setFilename(doc.get("filename").getAsString());
        resource.setUploadDate(doc.get("uploadDate").getAsString());
        resource.setAddedBy(doc.get("addedBy").getAsString());
        resource.setPublisher(doc.get("Publisher").getAsString());
        resource.setLinkToLicense(doc.get("linkToLicense").getAsString());
        resource.setOpenWith(doc.get("openWith").getAsString());
        resource.setArticleDate(doc.get("articleDate").getAsString());
        resource.setKind(doc.get("kind").getAsString());
        resource.setLanguage(doc.get("language").getAsString());
        resource.setAuthor(doc.get("author").getAsString());
        resource.setMediaType(doc.get("mediaType").getAsString());
        resource.setSendOnAccept(doc.get("sendOnAccept").getAsString());
        resource.setSum(doc.get("sum").getAsInt());
        resource.setTimesRated(doc.get("timesRated").getAsInt());
        resource.setResourceFor(doc.get("resourceFor").getAsJsonArray(), resource);
        resource.setSubject(doc.get("subject").getAsJsonArray(), resource);
        resource.setLevel(doc.get("level").getAsJsonArray(), resource);
        resource.setTag(doc.get("tag").getAsJsonArray(), resource);
        resource.setLanguages(doc.get("languages").getAsJsonArray(), resource);
    }

    public String getResource_id() {
        return resource_id;
    }

    public void setResource_id(String resource_id) {
        this.resource_id = resource_id;
    }

    public RealmList<String> getResourceFor() {
        return resourceFor;
    }

    public void setResourceFor(RealmList<String> resourceFor) {
        this.resourceFor = resourceFor;
    }

    public void setResourceFor(JsonArray array, realm_resources resource) {
        for (JsonElement s :
                array) {
            resource.resourceFor.add(s.getAsString());
        }
    }

    public void setSubject(JsonArray array, realm_resources resource) {
        for (JsonElement s :
                array) {
            resource.subject.add(s.getAsString());
        }
    }

    public void setLevel(JsonArray array, realm_resources resource) {
        for (JsonElement s :
                array) {
            resource.level.add(s.getAsString());
        }
    }

    public void setTag(JsonArray array, realm_resources resource) {
        for (JsonElement s :
                array) {
            resource.tag.add(s.getAsString());
        }
    }

    public void setLanguages(JsonArray array, realm_resources resource) {
        for (JsonElement s :
                array) {
            resource.languages.add(s.getAsString());
        }
    }

    public RealmList<String> getSubject() {
        return subject;
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


}
