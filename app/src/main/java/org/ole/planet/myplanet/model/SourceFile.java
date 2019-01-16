package org.ole.planet.myplanet.model;

import java.util.ArrayList;
import java.util.List;

public class SourceFile {
    private String mTitle;
    private String mResType;
    private String mURL;
    private String mDate;

    private List mSubjects = new ArrayList();
    private List mLevels = new ArrayList();

    // Default Constructor
    public SourceFile() {
    }

    public SourceFile(String title, String resType, String URL, String date, List subjects, List levels) {
        mTitle = title;
        mResType = resType;
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

    public String getDate() {
        return mDate;
    }

    public void setDate(String date) {
        mDate = date;
    }

}
