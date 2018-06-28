package org.ole.planet.takeout.Data;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class realm_stepResources extends RealmObject {
    @PrimaryKey
    private String id;
    private String resource_rev;
    private String title;
    private String author;
    private String year;
    private String description;
    private String language;
    private String publisher;
    private String linkToLicense;
    private String subject;
    private String level;
    private String openWith;
    private String resourceFor;
    private String medium;
    private String articleDate;
    private String resourceType;
    private String addedBy;
    private String openUrl;
    private String openWhichFile;
    private String isDownloadable;
    private String filename;
    private String filenmediaTypeame;
    private String resourceRemoteAddress;
    private String resourceLocalAddress;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getLinkToLicense() {
        return linkToLicense;
    }

    public void setLinkToLicense(String linkToLicense) {
        this.linkToLicense = linkToLicense;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getOpenWith() {
        return openWith;
    }

    public void setOpenWith(String openWith) {
        this.openWith = openWith;
    }

    public String getResourceFor() {
        return resourceFor;
    }

    public void setResourceFor(String resourceFor) {
        this.resourceFor = resourceFor;
    }

    public String getMedium() {
        return medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public String getArticleDate() {
        return articleDate;
    }

    public void setArticleDate(String articleDate) {
        this.articleDate = articleDate;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public String getOpenUrl() {
        return openUrl;
    }

    public void setOpenUrl(String openUrl) {
        this.openUrl = openUrl;
    }

    public String getOpenWhichFile() {
        return openWhichFile;
    }

    public void setOpenWhichFile(String openWhichFile) {
        this.openWhichFile = openWhichFile;
    }

    public String getIsDownloadable() {
        return isDownloadable;
    }

    public void setIsDownloadable(String isDownloadable) {
        this.isDownloadable = isDownloadable;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilenmediaTypeame() {
        return filenmediaTypeame;
    }

    public void setFilenmediaTypeame(String filenmediaTypeame) {
        this.filenmediaTypeame = filenmediaTypeame;
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
}