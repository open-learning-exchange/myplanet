package org.ole.planet.myplanet.services.sync

object SyncPolicy {
    val bootstrapDeferredTables = setOf(
        "courses_progress",
        "submissions",
        "login_activities",
        "notifications",
        "chat_history",
        "team_activities"
    )

    fun applyBootstrapPolicy(tables: List<String>, hasDeviceUsers: Boolean): List<String> {
        if (hasDeviceUsers) {
            return tables
        }
        return tables.filterNot { it in bootstrapDeferredTables }
    }
}
