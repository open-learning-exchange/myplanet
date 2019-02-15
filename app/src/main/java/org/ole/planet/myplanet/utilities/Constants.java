package org.ole.planet.myplanet.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;

import java.util.ArrayList;
import java.util.List;

public class Constants {
    public static List<ShelfData> shelfDataList;
    public static List<String> betaList;
    public static final String KEY_RATING = "rating";
    public static final String KEY_COURSE = "course";
    public static final String KEY_EXAM = "exam";
    public static final String KEY_RESOURCE = "resource";
    public static final String KEY_SYNC = "sync";
    public static final String KEY_SURVEY = "survey";
    public static final String KEY_MEETUPS = "meetup";
    public static final String KEY_TEAMS = "teams";
    public static final String KEY_DELETE = "delete";

    static {
        shelfDataList = new ArrayList<>();
        shelfDataList.add(new ShelfData("resourceIds", "resources", "resourceId", RealmMyLibrary.class));
        shelfDataList.add(new ShelfData("meetupIds", "meetups", "meetupId", RealmMeetup.class));
        shelfDataList.add(new ShelfData("courseIds", "courses", "courseId", RealmMyCourse.class));
        shelfDataList.add(new ShelfData("myTeamIds", "teams", "teamId", RealmMyTeam.class));

        betaList = new ArrayList<>();
        betaList.add(KEY_RATING);
        betaList.add(KEY_EXAM);
        betaList.add(KEY_TEAMS);
        betaList.add(KEY_MEETUPS);
        betaList.add(KEY_SURVEY);
        betaList.add(KEY_DELETE);
        betaList.add(KEY_DELETE);
        betaList.add(KEY_SYNC);
    }

    public static class ShelfData {
        public String key;
        public String type;
        public String categoryKey;
        public Class aClass;

        public ShelfData(String key, String type, String categoryKey, Class aClass) {
            this.key = key;
            this.type = type;
            this.categoryKey = categoryKey;
            this.aClass = aClass;
        }
    }


    public static boolean showBetaFeature(String s, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (betaList.contains(s)) {
            Utilities.log("return " + preferences.getBoolean("beta_function", false));
            return preferences.getBoolean("beta_function", false);
        }
        return true;
    }


}
