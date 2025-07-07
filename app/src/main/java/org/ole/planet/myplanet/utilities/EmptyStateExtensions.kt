package org.ole.planet.myplanet.utilities

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import org.ole.planet.myplanet.R

/**
 * Utility extension to update an empty state text view based on list count.
 */

private val emptyMessages = mapOf(
    "courses" to R.string.no_courses,
    "resources" to R.string.no_resources,
    "finances" to R.string.no_finance_record,
    "news" to R.string.no_voices_available,
    "teamCourses" to R.string.no_team_courses,
    "teamResources" to R.string.no_team_resources,
    "tasks" to R.string.no_tasks,
    "members" to R.string.no_join_request_available,
    "discussions" to R.string.no_news,
    "survey" to R.string.no_surveys,
    "survey_submission" to R.string.no_survey_submissions,
    "exam_submission" to R.string.no_exam_submissions,
    "team" to R.string.no_teams,
    "enterprise" to R.string.no_enterprise,
    "chatHistory" to R.string.no_chats,
    "feedback" to R.string.no_feedback,
    "reports" to R.string.no_reports
)

fun TextView.showEmpty(count: Int, source: String) {
    visibility = if (count == 0) View.VISIBLE else View.GONE
    @StringRes val res = emptyMessages[source]
        ?: R.string.no_data_available_please_check_and_try_again
    setText(res)
}
