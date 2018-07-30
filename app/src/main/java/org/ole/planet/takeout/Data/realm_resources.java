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
    public String id;
    public String resource_id;
    public String _rev;
    public String addedBy;
    public String uploadDate;
    public String openWith;
    public String articleDate;
    public String kind;
    public String language;
    public String author;
    public String year;
    public String title;
    public String averageRating;
    public String filename;
    public String mediaType;
    public String description;
    public int sum;
    public int timesRated;
    public RealmList<String> resourceFor;
    public RealmList<String> subject;
    public RealmList<String> level;
    public RealmList<String> tag;
    public RealmList<String> languages;


    public static void insertResources(JsonObject doc, Realm mRealm) {
        if (!mRealm.isInTransaction()) {
            mRealm.beginTransaction();
        }
        realm_resources resource = mRealm.createObject(realm_resources.class, UUID.randomUUID().toString());
        resource._rev = doc.get("_rev").getAsString();
        resource.resource_id = doc.get("_id").getAsString();
        resource.title = doc.get("title").getAsString();
        resource.description = doc.get("description").getAsString();
        resource.year = (doc.get("year").getAsString());
        resource.averageRating = (doc.has("averageRating") ? doc.get("averageRating").getAsString() : "");
        resource.filename = (doc.get("filename").getAsString());
        if (doc.has("uploadDate") && doc.get("uploadDate") != null)
            resource.uploadDate = (doc.get("uploadDate").getAsString());
        resource.addedBy = (doc.get("addedBy").getAsString());
        resource.openWith = (doc.get("openWith").getAsString());
        resource.articleDate = (doc.get("articleDate").getAsString());
        if (doc.has("kind") && doc.get("kind") != null)
            resource.kind = (doc.get("kind").getAsString());
        resource.language = (doc.get("language").getAsString());
        resource.author = (doc.get("author").getAsString());
        resource.mediaType = (doc.get("mediaType").getAsString());
        resource.timesRated = (doc.get("timesRated").getAsInt());
        resource.setResourceFor(doc.get("resourceFor").getAsJsonArray(), resource);
        resource.setSubject(doc.get("subject").getAsJsonArray(), resource);
        resource.setLevel(doc.get("level").getAsJsonArray(), resource);
        resource.setTag(doc.get("tag").getAsJsonArray(), resource);
        resource.setLanguages(doc.get("languages").getAsJsonArray(), resource);
        mRealm.commitTransaction();
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


}
