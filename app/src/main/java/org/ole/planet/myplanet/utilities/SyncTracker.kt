package org.ole.planet.myplanet.utilities

import android.content.Context
import android.util.Log
import org.ole.planet.myplanet.callback.SyncListener

object SyncTracker {

    // Sync type constants
    const val CHAT_HISTORY = "chat_history"
    const val TEAMS = "teams"
    const val FEEDBACK = "feedback"
    const val ACHIEVEMENTS = "achievements"
    const val HEALTH = "health"
    const val COURSES = "courses"
    const val RESOURCES = "resources"

    /**
     * Check if a specific sync type has been completed
     */
    fun hasSyncBeenCompleted(context: Context, syncType: String): Boolean {
        val sharedPrefManager = SharedPrefManager(context)
        return when (syncType) {
            CHAT_HISTORY -> sharedPrefManager.isChatHistorySynced()
            TEAMS -> sharedPrefManager.isTeamsSynced()
            FEEDBACK -> sharedPrefManager.isFeedbackSynced()
            ACHIEVEMENTS -> sharedPrefManager.isAchievementsSynced()
            HEALTH -> sharedPrefManager.isHealthSynced()
            COURSES -> sharedPrefManager.isCoursesSynced()
            RESOURCES -> sharedPrefManager.isResourcesSynced()
            else -> {
                Log.w("SyncTracker", "Unknown sync type: $syncType")
                false
            }
        }
    }

    /**
     * Mark a specific sync type as completed
     */
    fun markSyncAsCompleted(context: Context, syncType: String) {
        val sharedPrefManager = SharedPrefManager(context)
        when (syncType) {
            CHAT_HISTORY -> sharedPrefManager.setChatHistorySynced(true)
            TEAMS -> sharedPrefManager.setTeamsSynced(true)
            FEEDBACK -> sharedPrefManager.setFeedbackSynced(true)
            ACHIEVEMENTS -> sharedPrefManager.setAchievementsSynced(true)
            HEALTH -> sharedPrefManager.setHealthSynced(true)
            COURSES -> sharedPrefManager.setCoursesSynced(true)
            RESOURCES -> sharedPrefManager.setResourcesSynced(true)
            else -> Log.w("SyncTracker", "Unknown sync type: $syncType")
        }
        Log.d("SyncTracker", "Marked $syncType as completed")
    }

    /**
     * Mark a specific sync type as not completed
     */
    fun markSyncAsNotCompleted(context: Context, syncType: String) {
        val sharedPrefManager = SharedPrefManager(context)
        when (syncType) {
            CHAT_HISTORY -> sharedPrefManager.setChatHistorySynced(false)
            TEAMS -> sharedPrefManager.setTeamsSynced(false)
            FEEDBACK -> sharedPrefManager.setFeedbackSynced(false)
            ACHIEVEMENTS -> sharedPrefManager.setAchievementsSynced(false)
            HEALTH -> sharedPrefManager.setHealthSynced(false)
            COURSES -> sharedPrefManager.setCoursesSynced(false)
            RESOURCES -> sharedPrefManager.setResourcesSynced(false)
            else -> Log.w("SyncTracker", "Unknown sync type: $syncType")
        }
        Log.d("SyncTracker", "Marked $syncType as not completed")
    }

    /**
     * Get the timestamp when sync was completed
     */
    fun getSyncCompletionTime(context: Context, syncType: String): Long {
        val sharedPrefManager = SharedPrefManager(context)
        val timeKey = when (syncType) {
            CHAT_HISTORY -> "chat_history_synced"
            TEAMS -> "teams_synced"
            FEEDBACK -> "feedback_synced"
            ACHIEVEMENTS -> "achievements_synced"
            HEALTH -> "health_synced"
            COURSES -> "courses_synced"
            RESOURCES -> "resources_synced"
            else -> {
                Log.w("SyncTracker", "Unknown sync type: $syncType")
                return 0
            }
        }
        return sharedPrefManager.getSyncTime(timeKey)
    }

    /**
     * Reset sync status for a specific type
     */
    fun resetSyncStatus(context: Context, syncType: String) {
        val sharedPrefManager = SharedPrefManager(context)
        val resetKey = when (syncType) {
            CHAT_HISTORY -> "chat_history_synced"
            TEAMS -> "teams_synced"
            FEEDBACK -> "feedback_synced"
            ACHIEVEMENTS -> "achievements_synced"
            HEALTH -> "health_synced"
            COURSES -> "courses_synced"
            RESOURCES -> "resources_synced"
            else -> {
                Log.w("SyncTracker", "Unknown sync type: $syncType")
                return
            }
        }
        sharedPrefManager.resetSyncStatus(resetKey)
        Log.d("SyncTracker", "Reset sync status for $syncType")
    }

    /**
     * Reset all sync statuses
     */
    fun resetAllSyncStatuses(context: Context) {
        val sharedPrefManager = SharedPrefManager(context)
        sharedPrefManager.resetAllSyncStatuses()
        Log.d("SyncTracker", "Reset all sync statuses")
    }

    /**
     * Get all sync statuses for debugging
     */
    fun getAllSyncStatuses(context: Context): Map<String, Boolean> {
        val sharedPrefManager = SharedPrefManager(context)
        return mapOf(
            "Chat History" to sharedPrefManager.isChatHistorySynced(),
            "Teams" to sharedPrefManager.isTeamsSynced(),
            "Feedback" to sharedPrefManager.isFeedbackSynced(),
            "Achievements" to sharedPrefManager.isAchievementsSynced(),
            "Health" to sharedPrefManager.isHealthSynced(),
            "Courses" to sharedPrefManager.isCoursesSynced(),
            "Resources" to sharedPrefManager.isResourcesSynced()
        )
    }

    /**
     * Get all sync completion times for debugging
     */
    fun getAllSyncTimes(context: Context): Map<String, Long> {
        return mapOf(
            "Chat History" to getSyncCompletionTime(context, CHAT_HISTORY),
            "Teams" to getSyncCompletionTime(context, TEAMS),
            "Feedback" to getSyncCompletionTime(context, FEEDBACK),
            "Achievements" to getSyncCompletionTime(context, ACHIEVEMENTS),
            "Health" to getSyncCompletionTime(context, HEALTH),
            "Courses" to getSyncCompletionTime(context, COURSES),
            "Resources" to getSyncCompletionTime(context, RESOURCES)
        )
    }

    /**
     * Check how many days since last sync
     */
    fun getDaysSinceLastSync(context: Context, syncType: String): Long {
        val syncTime = getSyncCompletionTime(context, syncType)
        if (syncTime == 0L) return Long.MAX_VALUE // Never synced

        val currentTime = System.currentTimeMillis()
        val diffInMillis = currentTime - syncTime
        return diffInMillis / (1000 * 60 * 60 * 24) // Convert to days
    }

    /**
     * Check if sync is older than specified days
     */
    fun isSyncOlderThan(context: Context, syncType: String, days: Int): Boolean {
        return getDaysSinceLastSync(context, syncType) > days
    }

    /**
     * Utility method to handle common sync pattern
     */
    fun executeIfNotSynced(
        context: Context,
        syncType: String,
        onNeedSync: () -> Unit,
        onAlreadySynced: () -> Unit
    ) {
        if (hasSyncBeenCompleted(context, syncType)) {
            Log.d("SyncTracker", "$syncType already synced, executing onAlreadySynced")
            onAlreadySynced()
        } else {
            Log.d("SyncTracker", "$syncType not synced, executing onNeedSync")
            onNeedSync()
        }
    }

    /**
     * Create a standard SyncListener that handles marking sync as completed
     */
    fun createSyncListener(
        context: Context,
        syncType: String,
        onSyncStarted: (() -> Unit)? = null,
        onSyncComplete: (() -> Unit)? = null,
        onSyncFailed: ((String?) -> Unit)? = null
    ): SyncListener {
        return object : SyncListener {
            override fun onSyncStarted() {
                Log.d("SyncTracker", "Sync started for $syncType")
                onSyncStarted?.invoke()
            }

            override fun onSyncComplete() {
                Log.d("SyncTracker", "Sync completed for $syncType")
                markSyncAsCompleted(context, syncType)
                onSyncComplete?.invoke()
            }

            override fun onSyncFailed(message: String?) {
                Log.e("SyncTracker", "Sync failed for $syncType: $message")
                // Don't mark as completed on failure
                onSyncFailed?.invoke(message)
            }
        }
    }
}