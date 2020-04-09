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
import org.ole.planet.myplanet.model.RealmMyHealth;
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
import java.util.Map;

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
    public static final String DISCLAIMER = "<td>\n" +
            "          <h1>Disclaimer</h1>\n" +
            "<p>Last updated: January 10, 2020</p>\n" +
            "<h1>Interpretation and Definitions</h1>\n" +
            "<h2>Interpretation</h2>\n" +
            "<p>The words of which the initial letter is capitalized have meanings defined under the following conditions.</p>\n" +
            "<p>The following definitions shall have the same meaning regardless of whether they appear in singular or in plural.</p>\n" +
            "<h2>Definitions</h2>\n" +
            "<p>For the purposes of this Disclaimer:</p>\n" +
            "<ul>\n" +
            "    <li><strong>Company</strong> (referred to as either \"the Company\", \"We\", \"Us\" or \"Our\" in this Cookies Policy) refers to myPlanet.</li>\n" +
            "    <li><strong>You</strong> means the individual accessing the Service, or the company, or other legal entity on behalf of which such individual is accessing or using the Service, as applicable.</li>\n" +
            "        <li><strong>Application</strong> means the software program provided by the Company downloaded by You on any electronic device named myPlanet.</li>    <li><strong>Service</strong> refers to the Application.</li>\n" +
            "</ul>\n" +
            "<h1>Disclaimer</h1>\n" +
            "<p>The information contained on the Service is for general information purposes only.</p>\n" +
            "<p>The Company assumes no responsibility for errors or omissions in the contents of the Service.</p>\n" +
            "<p>In no event shall the Company be liable for any special, direct, indirect, consequential, or incidental damages or any damages whatsoever, whether in an action of contract, negligence or other tort, arising out of or in connection with the use of the Service or the contents of the Service. The Company reserves the right to make additions, deletions, or modifications to the contents on the Service at any time without prior notice.</p>\n" +
            "<p>The Company does not warrant that the Service is free of viruses or other harmful components.</p>\n" +
            "<h1>Medical Information Disclaimer</h1>\n" +
            "<p>The information about health provided by the Service is not intended to diagnose, treat, cure or prevent disease. Products, services, information and other content provided by the Service, including information linking to third-party websites are provided for informational purposes only.</p>\n" +
            "<p>Information offered by the Service is not comprehensive and does not cover all diseases, ailments, physical conditions or their treatment.</p>\n" +
            "<p>Individuals are different and may react differently to different products. Comments made on the Service by employees or other users are strictly their own personal views made in their own personal capacity and are not claims made by the Company nor do they represent the position or view of the Company.</p>\n" +
            "<p>The Company is not liable for any information provided by the Service with regard to recommendations regarding supplements for any health purposes.</p>\n" +
            "<p>The Company makes no guarantee or warranty with respect to any products or services sold. The Company is not responsible for any damages for information or services provided even if the Company has been advised of the possibility of damages.</p>\n" +
            "<h1>External Links Disclaimer</h1>\n" +
            "<p>The Service may contain links to external websites that are not provided or maintained by or in any way affiliated with the Company.</p>\n" +
            "<p>Please note that the Company does not guarantee the accuracy, relevance, timeliness, or completeness of any information on these external websites.</p>\n" +
            "<h1>Errors and Omissions Disclaimer</h1>\n" +
            "<p>The information given by the Service is for general guidance on matters of interest only. Even if the Company takes every precaution to insure that the content of the Service is both current and accurate, errors can occur. Plus, given the changing nature of laws, rules and regulations, there may be delays, omissions or inaccuracies in the information contained on the Service.</p>\n" +
            "<p>The Company is not responsible for any errors or omissions, or for the results obtained from the use of this information.</p>\n" +
            "<h1>Fair Use Disclaimer</h1>\n" +
            "<p>The Company may use copyrighted material which has not always been specifically authorized by the copyright owner. The Company is making such material available for criticism, comment, news reporting, teaching, scholarship, or research.</p>\n" +
            "<p>The Company believes this constitutes a \"fair use\" of any such copyrighted material as provided for in section 107 of the United States Copyright law.</p>\n" +
            "<p>If You wish to use copyrighted material from the Service for your own purposes that go beyond fair use, You must obtain permission from the copyright owner.</p>\n" +
            "<h1>Views Expressed Disclaimer</h1>\n" +
            "<p>The Service may contain views and opinions which are those of the authors and do not necessarily reflect the official policy or position of any other author, agency, organization, employer or company, including the Company.</p>\n" +
            "<p>Comments published by users are their sole responsibility and the users will take full responsibility, liability and blame for any libel or litigation that results from something written in or as a direct result of something written in a comment. The Company is not liable for any comment published by users and reserve the right to delete any comment for any reason whatsoever.</p>\n" +
            "<h1>No Responsibility Disclaimer</h1>\n" +
            "<p>The information on the Service is provided with the understanding that the Company is not herein engaged in rendering legal, accounting, tax, or other professional advice and services. As such, it should not be used as a substitute for consultation with professional accounting, tax, legal or other competent advisers.</p>\n" +
            "<p>In no event shall the Company or its suppliers be liable for any special, incidental, indirect, or consequential damages whatsoever arising out of or in connection with your access or use or inability to access or use the Service.</p>\n" +
            "<h1>\"Use at Your Own Risk\" Disclaimer</h1>\n" +
            "<p>All information in the Service is provided \"as is\", with no guarantee of completeness, accuracy, timeliness or of the results obtained from the use of this information, and without warranty of any kind, express or implied, including, but not limited to warranties of performance, merchantability and fitness for a particular purpose.</p>\n" +
            "<p>The Company will not be liable to You or anyone else for any decision made or action taken in reliance on the information given by the Service or for any consequential, special or similar damages, even if advised of the possibility of such damages.</p>\n" +
            "<h2>Contact Us</h2>\n" +
            "<p>If you have any questions about this Disclaimer, You can contact Us:</p>\n" +
            "<ul>\n" +
            "        <li>By email: myplanet@ole.org</li>\n " +
            "        <li>By visiting this page on our website: https://ole.org/contact</li>\n" +
            "</ul>\n" +
            " </td>";
    public static final String ABOUT = "" +
            "<td>\n" +
            "<h3>MyPlanet</h3>\n" +
            "<p>myPlanet is a learning tool that is designed to work with Planet web application. It has been used to improve early education, secondary schools, village health, youth workforce development, and economic and community development.</p>\n" +
            "<p>Planet houses is a repository of free, open access and public domain resources to benefit all learners.</p>\n" +
            "\n" +
            "<p>myPlanet is designed to be available to everyone, everywhere, all the time. It is portable, affordable, scalable and sustainable. It runs on any android device such as tablets and mobile phones. It functions off, as well as on, the Internet. </p>\n" +
            "<p>This application enables schools and communities to have a complete multi-media library and learning system that periodically connects with Planet. Configured devices can contain the learners' personal dashboard.  " +
            "This ensures learners can read books on their shelf and take courses offline - i.e without connection to a central server. Learners are encouraged to rate from one to five stars the resources they use and the courses they take. Periodically learners can sync with a server. " +
            "Activity data are uploaded and new resources are downloaded in a matter of a few minutes unto myPlanet for offline use.\n" +
            "\n" +
            "<p>The dashboard also contains a record of achievements, a calendar of events, and an internal chat system for communicating with fellow members.</p>\n" +
            "<p>myPlanet has been proven highly effective in improving learning opportunities for over fifty thousand learners in more than 100 locations, in schools throughout Nepal, Ghana, Kenya, and Rwanda, with Syrian refugees in Jordan, Somali refugees in Kenya, and village health workers in Uganda.</p>" +
            "      </td>";
    public static final HashMap<Class, Integer> COLOR_MAP = new HashMap<>();
    public static HashMap<String, Class> classList = new HashMap<>();
    public static HashMap<String, String> LABELS = new HashMap<>();

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
        //if (betaList.contains(s)) {
        Utilities.log(s + " beta");
        Utilities.log(preferences.getBoolean("beta_function", false) + " beta");
        Utilities.log(preferences.getBoolean(s, false) + " beta");
        Utilities.log((preferences.getBoolean("beta_function", false) && preferences.getBoolean(s, false)) + "");
        return preferences.getBoolean("beta_function", false) && preferences.getBoolean(s, s.equals(KEY_NEWSADDIMAGE));
        //  }
//        return true;
    }


    public static boolean autoSynFeature(String s, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(s, false);
    }


}
