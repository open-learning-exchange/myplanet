package org.ole.planet.takeout.Data;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_myLibrary extends RealmObject {
    @PrimaryKey
    private String id;
    private String userId;
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
}