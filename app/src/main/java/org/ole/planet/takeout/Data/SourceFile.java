package org.ole.planet.takeout.Data;

import java.util.ArrayList;
import java.util.List;

public class SourceFile {
    private String mTitle, mAuthor, mYear, mDesc,
    mLang, mPubAtrri, mLinkLicense, mFileType,
    mMedia, mResType, mAddedBy, mURL, mDate;

    private List mSubjects = new ArrayList();
    private List mLevels = new ArrayList();

    // Default Constructor
    public  SourceFile() {
    }

    public SourceFile(String title, String author, String year, String desc, String lang, String pubAtrri, String linkLicense, String fileType, String media, String resType, String addedBy, String URL, String date, List subjects, List levels) {
        mTitle = title;
        mAuthor = author;
        mYear = year;
        mDesc = desc;
        mLang = lang;
        mPubAtrri = pubAtrri;
        mLinkLicense = linkLicense;
        mFileType = fileType;
        mMedia = media;
        mResType = resType;
        mAddedBy = addedBy;
        mURL = URL;
        mDate = date;
        mSubjects = subjects;
        mLevels = levels;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public void setAuthor(String author) {
        mAuthor = author;
    }

    public String getYear() {
        return mYear;
    }

    public void setYear(String year) {
        mYear = year;
    }

    public String getDesc() {
        return mDesc;
    }

    public void setDesc(String desc) {
        mDesc = desc;
    }

    public String getLang() {
        return mLang;
    }

    public void setLang(String lang) {
        mLang = lang;
    }

    public String getPubAtrri() {
        return mPubAtrri;
    }

    public void setPubAtrri(String pubAtrri) {
        mPubAtrri = pubAtrri;
    }

    public String getLinkLicense() {
        return mLinkLicense;
    }

    public void setLinkLicense(String linkLicense) {
        mLinkLicense = linkLicense;
    }

    public String getFileType() {
        return mFileType;
    }

    public void setFileType(String fileType) {
        mFileType = fileType;
    }

    public String getMedia() {
        return mMedia;
    }

    public void setMedia(String media) {
        mMedia = media;
    }

    public String getResType() {
        return mResType;
    }

    public void setResType(String resType) {
        mResType = resType;
    }

    public String getAddedBy() {
        return mAddedBy;
    }

    public void setAddedBy(String addedBy) {
        mAddedBy = addedBy;
    }

    public String getURL() {
        return mURL;
    }

    public void setURL(String URL) {
        mURL = URL;
    }

    public String getDate() {
        return mDate;
    }

    public void setDate(String date) {
        mDate = date;
    }

    public List getSubjects() {
        return mSubjects;
    }

    public void setSubjects(List subjects) {
        mSubjects = subjects;
    }

    public List getLevels() {
        return mLevels;
    }

    public void setLevels(List levels) {
        mLevels = levels;
    }
}
