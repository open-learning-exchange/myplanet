package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.di.PlainGson
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin

internal fun Submission.examIdFromParentId(): String? {
    return parentId?.substringBefore("@")
}

internal class SubmissionsPayloadBuilder @Inject constructor(
    private val teamsRepositoryProvider: Provider<TeamsRepository>,
    private val sharedPrefManager: SharedPrefManager,
    private val examDao: ExamDao,
    private val questionDao: QuestionDao,
    @PlainGson private val gson: Gson,
    private val deviceNameProvider: DeviceNameProvider
) {
    private data class PayloadData(
        val user: UserEntity?,
        val exam: StepExam?,
        val questions: List<ExamQuestion>
    )

    private suspend fun getPayloadData(submission: Submission, user: UserEntity?): PayloadData {
        val examId = submission.examIdFromParentId()
        val exam = examId?.let { examDao.getById(it) }
        val questions = exam?.id?.let { questionDao.getByExamId(it) } ?: emptyList()
        return PayloadData(user, exam, questions)
    }

    suspend fun examUploadPayload(submission: Submission, user: UserEntity?): JsonObject {
        val `object` = JsonObject()
        val payloadData = getPayloadData(submission, user)
        val resolvedUser = payloadData.user
        val exam = payloadData.exam

        if (!submission._id.isNullOrEmpty()) {
            `object`.addProperty("_id", submission._id)
        }
        if (!submission._rev.isNullOrEmpty()) {
            `object`.addProperty("_rev", submission._rev)
        }
        `object`.addProperty("parentId", submission.parentId)
        `object`.addProperty("type", submission.type)

        resolveTeamJson(submission)?.let { `object`.add("team", it) }

        `object`.addProperty("grade", submission.grade)
        `object`.addProperty("startTime", submission.startTime)
        `object`.addProperty("lastUpdateTime", submission.lastUpdateTime)
        `object`.addProperty("status", submission.status)
        `object`.addDocumentOrigin()
        `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", deviceNameProvider.getCustomDeviceName())
        `object`.addProperty("sender", submission.sender)
        `object`.addProperty("source", sharedPrefManager.getPlanetCode())
        `object`.addProperty("parentCode", sharedPrefManager.getParentCode())
        `object`.add("answers", Answer.serializeAnswer(submission.answers ?: mutableListOf()))
        if (exam != null) {
            `object`.add("parent", StepExam.serializeExam(exam, payloadData.questions))
        } else {
            val parent = gson.fromJson(submission.parent, JsonObject::class.java)
            `object`.add("parent", parent)
        }
        val freshUser = resolvedUser?.serialize()
        when {
            freshUser != null -> `object`.add("user", freshUser)
            !submission.user.isNullOrEmpty() -> `object`.add("user", JsonParser.parseString(submission.user))
        }
        return `object`
    }

    private suspend fun resolveTeamJson(submission: Submission): JsonObject? {
        val teamRef = submission.teamObject
        val teamId = teamRef?._id?.takeIf { it.isNotBlank() }
            ?: submission.teamId?.takeIf { it.isNotBlank() }
            ?: return null

        val teamName = teamRef?.name?.takeIf { it.isNotBlank() }
        val teamType = teamRef?.type?.takeIf { it.isNotBlank() }
        val localTeam = if (teamName == null || teamType == null) {
            getTeamByIdOrNull(teamId)
        } else {
            null
        }

        return JsonObject().apply {
            addProperty("_id", teamId)
            (teamName ?: localTeam?.name?.takeIf { it.isNotBlank() })?.let { addProperty("name", it) }
            (teamType ?: localTeam?.type?.takeIf { it.isNotBlank() })?.let { addProperty("type", it) }
        }
    }

    internal suspend fun getTeamByIdOrNull(teamId: String) = try {
        teamsRepositoryProvider.get().getTeamById(teamId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    suspend fun submissionPayload(submission: Submission, source: String, parentCode: String, user: UserEntity?): JsonObject {
        val jsonObject = JsonObject()

        try {
            val payloadData = getPayloadData(submission, user)
            val exam = payloadData.exam

            if (!submission._id.isNullOrEmpty()) {
                jsonObject.addProperty("_id", submission._id)
            }
            if (!submission._rev.isNullOrEmpty()) {
                jsonObject.addProperty("_rev", submission._rev)
            }

            jsonObject.addProperty("parentId", submission.parentId ?: "")
            jsonObject.addProperty("type", submission.type ?: "survey")
            resolveTeamJson(submission)?.let { jsonObject.add("team", it) }
            jsonObject.addProperty("grade", submission.grade)
            jsonObject.addProperty("startTime", submission.startTime)
            jsonObject.addProperty("lastUpdateTime", submission.lastUpdateTime)
            jsonObject.addProperty("status", submission.status ?: "pending")
            jsonObject.addDocumentOrigin()
            jsonObject.addProperty("deviceName", NetworkUtils.getDeviceName())
            jsonObject.addProperty("customDeviceName", deviceNameProvider.getCustomDeviceName())
            jsonObject.addProperty("sender", submission.sender)
            jsonObject.addProperty("source", source)
            jsonObject.addProperty("parentCode", parentCode)
            jsonObject.add("answers", Answer.serializeAnswer(submission.answers ?: mutableListOf()))
            if (exam != null) {
                jsonObject.add("parent", StepExam.serializeExam(exam, payloadData.questions))
            } else if (!submission.parent.isNullOrEmpty()) {
                jsonObject.add("parent", JsonParser.parseString(submission.parent))
            }

            val userJson = payloadData.user?.serialize()
                ?: submission.user?.takeIf { it.isNotEmpty() }?.let { JsonParser.parseString(it).asJsonObject }
            if (userJson != null) {
                if (submission.membershipDoc != null) {
                    val membershipJson = JsonObject()
                    membershipJson.addProperty("teamId", submission.membershipDoc?.teamId ?: "")
                    userJson.add("membershipDoc", membershipJson)
                }
                jsonObject.add("user", userJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return jsonObject
    }
}
