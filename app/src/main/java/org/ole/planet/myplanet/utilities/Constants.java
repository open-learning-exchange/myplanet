package org.ole.planet.myplanet.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Constants {
    public static List<ShelfData> shelfDataList;
    public static List<String> betaList;
    public static final String KEY_RATING = "beta_rating";
    public static final String KEY_EXAM = "beta_course";
    public static final String KEY_SYNC = "beta_wifi_switch";
    public static final String KEY_SURVEY = "beta_survey";
    public static final String KEY_MEETUPS = "key_meetup";
    public static final String KEY_TEAMS = "key_teams";
    public static final String KEY_DELETE = "key_delete";
    public static final String KEY_ACHIEVEMENT = "beta_achievement";
    public static final HashMap<Class, Integer> COLOR_MAP = new HashMap<>();
    static {
        shelfDataList = new ArrayList<>();
        shelfDataList.add(new ShelfData("resourceIds", "resources", "resourceId", RealmMyLibrary.class));
        shelfDataList.add(new ShelfData("meetupIds", "meetups", "meetupId", RealmMeetup.class));
        shelfDataList.add(new ShelfData("courseIds", "courses", "courseId", RealmMyCourse.class));
        shelfDataList.add(new ShelfData("myTeamIds", "teams", "teamId", RealmMyTeam.class));
        COLOR_MAP.put(RealmMyLibrary.class, R.color.md_red_500);
        COLOR_MAP.put(RealmMyCourse.class, R.color.md_amber_700);
        COLOR_MAP.put(RealmMyTeam.class, R.color.md_green_700);
        COLOR_MAP.put(RealmMeetup.class, R.color.md_purple_500);
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
        //if (betaList.contains(s)) {
        Utilities.log(s + " beta");
        Utilities.log(preferences.getBoolean("beta_function", false) + " beta");
        Utilities.log(preferences.getBoolean(s,false) + " beta");
        Utilities.log((preferences.getBoolean("beta_function", false) &&  preferences.getBoolean(s,false)) + "");
            return preferences.getBoolean("beta_function", false) &&  preferences.getBoolean(s,false);
      //  }
//        return true;
    }


}
