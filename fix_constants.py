import json

def get_constants_diff():
    return """<<<<<<< SEARCH
    const val PREFS_NAME = "OLE_PLANET"
    var classList = mutableMapOf<String, Class<*>>()
    var LABELS = mutableMapOf<String, String>()
    const val KEY_NOTIFICATION_SHOWN = "notification_shown"
    const val SELECTED_LANGUAGE = "app_language"
    const val ACTION_RETRY_EVENT = "ACTION_RETRY_EVENT"

    init {
        initClasses()
        shelfDataList = mutableListOf(
=======
    const val PREFS_NAME = "OLE_PLANET"
    var LABELS = mutableMapOf<String, String>()
    const val KEY_NOTIFICATION_SHOWN = "notification_shown"
    const val SELECTED_LANGUAGE = "app_language"
    const val ACTION_RETRY_EVENT = "ACTION_RETRY_EVENT"

    init {
        shelfDataList = mutableListOf(
>>>>>>> REPLACE
<<<<<<< SEARCH
            "Help wanted" to "help",
            "Request for advice" to "advice"
        )
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
        classList["health"] = RealmHealthExamination::class.java
        classList["certifications"] = RealmCertification::class.java
        classList["team_activities"] = RealmTeamLog::class.java
        classList["courses_progress"] = RealmCourseProgress::class.java
        classList["notifications"] = RealmNotification::class.java
    }

    @JvmStatic
    fun showBetaFeature(s: String, context: Context): Boolean {
=======
            "Help wanted" to "help",
            "Request for advice" to "advice"
        )
    }

    @JvmStatic
    fun showBetaFeature(s: String, context: Context): Boolean {
>>>>>>> REPLACE"""

with open("patch_constants.json", "w") as f:
    json.dump({"filepath": "app/src/main/java/org/ole/planet/myplanet/utils/Constants.kt", "merge_diff": get_constants_diff()}, f)
