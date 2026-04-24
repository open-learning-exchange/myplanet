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

    private val steadyStateBackgroundTables = setOf(
        "courses_progress",
        "notifications"
    )

    fun applyBootstrapPolicy(tables: List<String>, hasDeviceUsers: Boolean): List<String> {
        if (hasDeviceUsers) {
            return tables
        }
        return tables.filterNot { it in bootstrapDeferredTables }
    }

    fun applyForegroundPolicy(tables: List<String>, hasDeviceUsers: Boolean): List<String> {
        val bootstrapTables = applyBootstrapPolicy(tables, hasDeviceUsers)
        if (!hasDeviceUsers) {
            return bootstrapTables
        }
        return bootstrapTables.filterNot { it in steadyStateBackgroundTables }
    }

    fun backgroundTablesFor(tables: List<String>, hasDeviceUsers: Boolean): List<String> {
        if (!hasDeviceUsers) {
            return emptyList()
        }

        val backgroundTables = linkedSetOf<String>()
        if ("courses" in tables || "courses_progress" in tables) {
            backgroundTables += "courses_progress"
        }
        backgroundTables += "notifications"

        return backgroundTables.filter { it in steadyStateBackgroundTables }
    }
}
