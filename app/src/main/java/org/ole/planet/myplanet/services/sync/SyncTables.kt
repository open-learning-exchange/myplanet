package org.ole.planet.myplanet.services.sync

object SyncTables {
    object Core {
        const val TABLET_USERS = "tablet_users"
        const val EXAMS = "exams"
        const val RATINGS = "ratings"
        const val COURSES_PROGRESS = "courses_progress"
        const val ACHIEVEMENTS = "achievements"
        const val TAGS = "tags"
        const val SUBMISSIONS = "submissions"
        const val NEWS = "news"
        const val FEEDBACK = "feedback"
        const val TASKS = "tasks"
        const val LOGIN_ACTIVITIES = "login_activities"
        const val COURSES = "courses"
    }

    object Health {
        const val HEALTH = "health"
        const val CERTIFICATIONS = "certifications"
    }

    object Teams {
        const val TEAMS = "teams"
        const val TEAM_ACTIVITIES = "team_activities"
    }

    object Social {
        const val CHAT_HISTORY = "chat_history"
        const val MEETUPS = "meetups"
    }

    fun syncKey(table: String) = "${table}_sync"
}
