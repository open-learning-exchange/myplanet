package org.ole.planet.myplanet.model

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import java.util.UUID
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils

open class RealmSubmission : RealmObject() {
    @PrimaryKey
    var id: String? = null
    @Index
    var _id: String? = null
    @Index
    var _rev: String? = null
    var parentId: String? = null
    var type: String? = null
    var userId: String? = null
    var user: String? = null
    var startTime: Long = 0
    var lastUpdateTime: Long = 0
    var answers: RealmList<RealmAnswer>? = null
    var teamObject: RealmTeamReference? = null
    var grade: Long = 0
    var status: String? = null
    var uploaded = false
    var sender: String? = null
    var source: String? = null
    var parentCode: String? = null
    var parent: String? = null
    var membershipDoc: RealmMembershipDoc? = null
    @Index
    var isUpdated = false
    @Ignore
    var submitterName: String = ""

    companion object {
        @JvmStatic
        fun serialize(mRealm: Realm, submission: RealmSubmission, context: Context, spm: org.ole.planet.myplanet.services.SharedPrefManager): JsonObject {
            val jsonObject = JsonObject()

            try {
                var examId = submission.parentId
                if (submission.parentId?.contains("@") == true) {
                    examId = submission.parentId?.split("@".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()?.get(0)
                }
                val exam = mRealm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()

                if (!submission._id.isNullOrEmpty()) {
                    jsonObject.addProperty("_id", submission._id)
                }
                if (!submission._rev.isNullOrEmpty()) {
                    jsonObject.addProperty("_rev", submission._rev)
                }

                jsonObject.addProperty("parentId", submission.parentId ?: "")
                jsonObject.addProperty("type", submission.type ?: "survey")
                jsonObject.addProperty("grade", submission.grade)
                jsonObject.addProperty("startTime", submission.startTime)
                jsonObject.addProperty("lastUpdateTime", submission.lastUpdateTime)
                jsonObject.addProperty("status", submission.status ?: "pending")
                jsonObject.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
                jsonObject.addProperty("deviceName", NetworkUtils.getDeviceName())
                jsonObject.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
                jsonObject.addProperty("sender", submission.sender)
                jsonObject.addProperty("source", spm.getPlanetCode())
                jsonObject.addProperty("parentCode", spm.getParentCode())
                jsonObject.add("answers", RealmAnswer.serializeRealmAnswer(submission.answers ?: RealmList()))
                if (exam != null) {
                    jsonObject.add("parent", RealmStepExam.serializeExam(mRealm, exam))
                } else if (!submission.parent.isNullOrEmpty()) {
                    jsonObject.add("parent", JsonParser.parseString(submission.parent))
                }

                if (!submission.user.isNullOrEmpty()) {
                    val userJson = JsonParser.parseString(submission.user).asJsonObject
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
}

open class RealmMembershipDoc : RealmObject() {
    var teamId: String? = null
}

open class RealmTeamReference : RealmObject() {
    var _id: String? = null
    var name: String? = null
    var type: String? = null
}
