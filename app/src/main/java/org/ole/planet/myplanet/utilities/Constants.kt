package org.ole.planet.myplanet.utilities

import android.content.Context
import androidx.preference.PreferenceManager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask

object Constants {
    const val KEY_LOGIN = "isLoggedIn"
    const val DICTIONARY_URL = "http://157.245.241.39:8000/output.json"
    @JvmField
    var shelfDataList = mutableListOf<ShelfData>()
    const val KEY_SYNC = "beta_wifi_switch"
    const val KEY_MEETUPS = "key_meetup"
    const val KEY_AUTOSYNC_ = "auto_sync_with_server"
    const val KEY_AUTOSYNC_WEEKLY = "force_weekly_sync"
    const val KEY_AUTOSYNC_MONTHLY = "force_monthly_sync"
    const val KEY_NEWSADDIMAGE = "beta_addImageToMessage"
    const val KEY_UPGRADE_MAX = "beta_upgrade_max"
    const val DISCLAIMER = R.string.disclaimer
    const val ABOUT = R.string.about
    const val PREFS_NAME = "OLE_PLANET"
    private val COLOR_MAP = HashMap<Class<*>, Int>()
    @JvmField
    var classList = HashMap<String, Class<*>>()
    @JvmField
    var LABELS = HashMap<String, String>()
    const val KEY_NOTIFICATION_SHOWN = "notification_shown"
    const val SELECTED_LANGUAGE = "app_language"

    init {
        initClasses()
        shelfDataList = ArrayList()
        LABELS = HashMap()
        shelfDataList.add(ShelfData("resourceIds", "resources", "resourceId"))
        shelfDataList.add(ShelfData("meetupIds", "meetups", "meetupId"))
        shelfDataList.add(ShelfData("courseIds", "courses", "courseId"))
        shelfDataList.add(ShelfData("myTeamIds", "teams", "teamId"))
        COLOR_MAP[RealmMyLibrary::class.java] = R.color.md_red_200
        COLOR_MAP[RealmMyCourse::class.java] = R.color.md_amber_200
        COLOR_MAP[RealmMyTeam::class.java] = R.color.md_green_200
        COLOR_MAP[RealmMeetup::class.java] = R.color.md_purple_200
        LABELS["Help Wanted"] = "help"
        LABELS["Offer"] = "offer"
        LABELS["Request for advice"] = "advice"
    }

    private fun initClasses() {
        classList["news"] = RealmNews::class.java
        classList["tags"] = RealmTag::class.java
        classList["login_activities"] = RealmOfflineActivity::class.java
        classList["ratings"] = RealmRating::class.java
        classList["submissions"] = RealmSubmission::class.java
        classList["courses"] = RealmMyCourse::class.java
        classList["achievements"] = RealmAchievement::class.java
        classList["feedback"] = RealmFeedback::class.java
        classList["teams"] = RealmMyTeam::class.java
        classList["tasks"] = RealmTeamTask::class.java
        classList["meetups"] = RealmMeetup::class.java
        classList["health"] = RealmMyHealthPojo::class.java
        classList["certifications"] = RealmCertification::class.java
        classList["team_activities"] = RealmTeamLog::class.java
        classList["courses_progress"] = RealmCourseProgress::class.java
    }

    @JvmStatic
    fun showBetaFeature(s: String, context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getBoolean("beta_function", false) && preferences.getBoolean(s, s == KEY_NEWSADDIMAGE)
    }

    @JvmStatic
    fun autoSynFeature(s: String?, context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getBoolean(s, false)
    }

    class ShelfData(
        @JvmField var key: String,
        @JvmField var type: String,
        @JvmField var categoryKey: String
    )
}
