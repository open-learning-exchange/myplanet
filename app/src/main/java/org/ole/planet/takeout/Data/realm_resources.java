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
        resource.setYear(doc.get("year").getAsString());
        resource.setAverageRating(doc.has("averageRating") ? doc.get("averageRating").getAsString() : "");
        resource.setFilename(doc.get("filename").getAsString());
        resource.setUploadDate(doc.get("uploadDate").getAsString());
        resource.setAddedBy(doc.get("addedBy").getAsString());
        resource.setOpenWith(doc.get("openWith").getAsString());
        resource.setArticleDate(doc.get("articleDate").getAsString());
        resource.setKind(doc.get("kind").getAsString());
        resource.setLanguage(doc.get("language").getAsString());
        resource.setAuthor(doc.get("author").getAsString());
        resource.setMediaType(doc.get("mediaType").getAsString());
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




    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }


    public void setUploadDate(String uploadDate) {
        this.uploadDate = uploadDate;
    }


    public void setOpenWith(String openWith) {
        this.openWith = openWith;
    }


    public void setArticleDate(String articleDate) {
        this.articleDate = articleDate;
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


    public int getTimesRated() {
        return timesRated;
    }

    public void setTimesRated(int timesRated) {
        this.timesRated = timesRated;
    }


}
