package org.ole.planet.takeout.Data;

import io.realm.RealmObject;
import io.realm.annotations.Ignore;
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
    private String _attachments;
    private String resourceFor;
    private String mediaType;
    private String averageRating;
    private String description;
    private String resourceRemoteAddress;
    private String resourceLocalAddress;

    @Ignore
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public String get_attachments() {
        return _attachments;
    }

    public void set_attachments(String _attachments) {
        this._attachments = _attachments;
    }

    @Override
    public String toString() {
        return resource_rev + " " + resourceId + " " + resource_rev + " " + mediaType + " " + medium;
    }
}