package org.ole.planet.myplanet.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmCertification;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmOfflineActivity;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmTeamTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Constants {
    public static final String KEY_LOGIN = "isLoggedIn";
    public static final String DICTIONARY_URL = "http://157.245.241.39:8000/output.json";
    public static List<ShelfData> shelfDataList;
    public static final String KEY_RATING = "beta_rating";
    public static final String KEY_EXAM = "beta_course";
    public static final String KEY_SYNC = "beta_wifi_switch";
    public static final String KEY_SURVEY = "beta_survey";
    public static final String KEY_MEETUPS = "key_meetup";
    public static final String KEY_TEAMS = "key_teams";
    public static final String KEY_DELETE = "key_delete";
    public static final String KEY_ACHIEVEMENT = "beta_achievement";
    public static final String KEY_MYHEALTH = "beta_myHealth";
    public static final String KEY_HEALTHWORKER = "beta_healthWorker";
    public static final String KEY_AUTOSYNC_ = "auto_sync_with_server";
    public static final String KEY_AUTOSYNC_WEEKLY = "force_weekly_sync";
    public static final String KEY_AUTOSYNC_MONTHLY = "force_monthly_sync";
    public static final String KEY_NEWSADDIMAGE = "beta_addImageToMessage";
    public static final String KEY_AUTOUPDATE = "beta_auto_update";
    public static final String KEY_UPGRADE_MAX = "beta_upgrade_max";
    public static final int DISCLAIMER = R.string.disclaimer;
    public static final int ABOUT = R.string.about;
    public static final HashMap<Class, Integer> COLOR_MAP = new HashMap<>();
    public static HashMap<String, Class> classList = new HashMap<>();
    public static HashMap<String, String> LABELS = new HashMap<>();

    public static final String KEY_NOTIFICATION_SHOWN = "notification_shown";

    static {
        initClasses();
        shelfDataList = new ArrayList<>();
        LABELS = new HashMap<>();
        shelfDataList.add(new ShelfData("resourceIds", "resources", "resourceId", RealmMyLibrary.class));
        shelfDataList.add(new ShelfData("meetupIds", "meetups", "meetupId", RealmMeetup.class));
        shelfDataList.add(new ShelfData("courseIds", "courses", "courseId", RealmMyCourse.class));
        shelfDataList.add(new ShelfData("myTeamIds", "teams", "teamId", RealmMyTeam.class));
        COLOR_MAP.put(RealmMyLibrary.class, R.color.md_red_200);
        COLOR_MAP.put(RealmMyCourse.class, R.color.md_amber_200);
        COLOR_MAP.put(RealmMyTeam.class, R.color.md_green_200);
        COLOR_MAP.put(RealmMeetup.class, R.color.md_purple_200);
        LABELS.put("Help Wanted", "help");
        LABELS.put("Offer", "offer");
        LABELS.put("Request for advice", "advice");
    }

    private static void initClasses() {
        classList.put("news", RealmNews.class);
        classList.put("tags", RealmTag.class);
        classList.put("login_activities", RealmOfflineActivity.class);
        classList.put("ratings", RealmRating.class);
        classList.put("submissions", RealmSubmission.class);
        classList.put("courses", RealmMyCourse.class);
        classList.put("achievements", RealmAchievement.class);
        classList.put("feedback", RealmFeedback.class);
        classList.put("teams", RealmMyTeam.class);
        classList.put("tasks", RealmTeamTask.class);
        classList.put("meetups", RealmMeetup.class);
        classList.put("health", RealmMyHealthPojo.class);
        classList.put("certifications", RealmCertification.class);
        classList.put("team_activities", RealmTeamLog.class);
        classList.put("courses_progress", RealmCourseProgress.class);
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
        Utilities.log(s + " beta");
        Utilities.log(preferences.getBoolean("beta_function", false) + " beta");
        Utilities.log(preferences.getBoolean(s, false) + " beta");
        Utilities.log((preferences.getBoolean("beta_function", false) && preferences.getBoolean(s, false)) + "");
        return preferences.getBoolean("beta_function", false) && preferences.getBoolean(s, s.equals(KEY_NEWSADDIMAGE));
    }

    public static boolean autoSynFeature(String s, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(s, false);
    }
}