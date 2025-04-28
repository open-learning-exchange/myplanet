package org.ole.planet.myplanet.service

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmChatHistory
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
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.RealmTransactionManager
import org.ole.planet.myplanet.utilities.SyncTimingLogger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RealmBatchProcessor(private val realm: Realm, private val batchSize: Int = 1000) {
    private val batches = mutableMapOf<String, MutableList<JsonObject>>()
    private val typeProcessors = mutableMapOf<String, (List<JsonObject>, Realm) -> Unit>()
    private val processingLock = ReentrantLock()

    init {
        // Register batch processors for each type
        registerTypeProcessor("resources") { batch, r -> batchInsertResources(batch, r) }
        registerTypeProcessor("courses") { batch, r -> batchInsertCourses(batch, r) }
        registerTypeProcessor("achievements") { batch, r -> batchInsertAchievements(batch, r) }
        registerTypeProcessor("courses_progress") { batch, r -> batchInsertProgress(batch, r) }
        registerTypeProcessor("chat_history") { batch, r -> batchInsertChatHistory(batch, r) }
        registerTypeProcessor("exams") { batch, r -> batchInsertExams(batch, r) }
        registerTypeProcessor("feedback") { batch, r -> batchInsertFeedback(batch, r) }
        registerTypeProcessor("health") { batch, r -> batchInsertHealth(batch, r) }
        registerTypeProcessor("login_activities") { batch, r -> batchInsertLoginActivities(batch, r) }
        registerTypeProcessor("meetups") { batch, r -> batchInsertMeetups(batch, r) }
        registerTypeProcessor("news") { batch, r -> batchInsertNews(batch, r) }
        registerTypeProcessor("ratings") { batch, r -> batchInsertRatings(batch, r) }
        registerTypeProcessor("submissions") { batch, r -> batchInsertSubmissions(batch, r) }
        registerTypeProcessor("tags") { batch, r -> batchInsertTags(batch, r) }
        registerTypeProcessor("teams") { batch, r -> batchInsertTeams(batch, r) }
        registerTypeProcessor("tasks") { batch, r -> batchInsertTasks(batch, r) }
        registerTypeProcessor("team_activities") { batch, r -> batchInsertTeamActivities(batch, r) }
        registerTypeProcessor("tablet_users") { batch, r -> batchInsertUsers(batch, r) }
        registerTypeProcessor("certifications") { batch, r -> batchInsertCertifications(batch, r) }
    }

    fun registerTypeProcessor(type: String, processor: (List<JsonObject>, Realm) -> Unit) {
        typeProcessors[type] = processor
    }

    fun addToBatch(type: String, doc: JsonObject) {
        processingLock.withLock {
            if (!batches.containsKey(type)) {
                batches[type] = mutableListOf()
            }

            batches[type]?.add(doc)

            // Process batch if it reaches the threshold
            if ((batches[type]?.size ?: 0) >= batchSize) {
                try {
                    processBatch(type)
                } catch (e: Exception) {
                    Log.e("RealmBatchProcessor", "Error processing batch for $type: ${e.message}")
                }
            }
        }
    }

    fun processBatch(type: String) {
        val batch = batches[type] ?: return
        if (batch.isEmpty()) return

        val processor = typeProcessors[type] ?: return

        SyncTimingLogger.logOperation("batch_process_${type}_start")
        // Use RealmTransactionUtil to handle the transaction
        RealmTransactionManager.executeInTransaction(realm) { realmInstance ->
            processor(batch, realmInstance)
        }
        SyncTimingLogger.logOperation("batch_process_${type}_complete")

        // Clear the processed batch
        batch.clear()
    }

    fun flushAll() {
        SyncTimingLogger.logOperation("batch_flush_all_start")
        batches.keys.forEach { type ->
            processBatch(type)
        }
        SyncTimingLogger.logOperation("batch_flush_all_complete")
    }

    // Batch processing implementations
    private fun batchInsertResources(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmMyLibrary.insertMyLibrary("", doc, realm)
        }
    }

    private fun batchInsertCourses(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmMyCourse.insert(realm, doc)
        }
    }

    private fun batchInsertAchievements(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmAchievement.insert(realm, doc)
        }
    }

    private fun batchInsertProgress(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmCourseProgress.insert(realm, doc)
        }
    }

    private fun batchInsertChatHistory(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmChatHistory.insert(realm, doc)
        }
    }

    private fun batchInsertExams(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmStepExam.insertCourseStepsExams("", "", doc, realm)
        }
    }

    private fun batchInsertFeedback(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmFeedback.insert(realm, doc)
        }
    }

    private fun batchInsertHealth(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmMyHealthPojo.insert(realm, doc)
        }
    }

    private fun batchInsertLoginActivities(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmOfflineActivity.insert(realm, doc)
        }
    }

    private fun batchInsertMeetups(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmMeetup.insert(realm, doc)
        }
    }

    private fun batchInsertNews(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmNews.insert(realm, doc)
        }
    }

    private fun batchInsertRatings(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmRating.insert(realm, doc)
        }
    }

    private fun batchInsertSubmissions(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmSubmission.insert(realm, doc)
        }
    }

    private fun batchInsertTags(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmTag.insert(realm, doc)
        }
    }

    private fun batchInsertTeams(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmMyTeam.insert(realm, doc)
        }
    }

    private fun batchInsertTasks(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmTeamTask.insert(realm, doc)
        }
    }

    private fun batchInsertTeamActivities(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmTeamLog.insert(realm, doc)
        }
    }

    private fun batchInsertUsers(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmUserModel.populateUsersTable(doc, realm, context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
        }
    }

    private fun batchInsertCertifications(batch: List<JsonObject>, realm: Realm) {
        for (doc in batch) {
            RealmCertification.insert(realm, doc)
        }
    }
}