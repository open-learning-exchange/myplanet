package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourseByUserId
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.isMyCourse
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities

open class RealmCourseProgress : RealmObject() {
    @PrimaryKey
    var id: String? = null
    private var _id: String? = null
    @JvmField
    var createdOn: String? = null
    @JvmField
    var createdDate: Long = 0
    @JvmField
    var updatedDate: Long = 0
    private var _rev: String? = null
    @JvmField
    var stepNum = 0
    @JvmField
    var passed = false
    @JvmField
    var userId: String? = null
    @JvmField
    var courseId: String? = null
    @JvmField
    var parentCode: String? = null
    fun get_rev(): String? {
        return _rev
    }

    fun set_rev(_rev: String?) {
        this._rev = _rev
    }

    fun get_id(): String? {
        return _id
    }

    fun set_id(_id: String?) {
        this._id = _id
    }

    companion object {
        @JvmStatic
        fun serializeProgress(progress: RealmCourseProgress): JsonObject {
            val `object` = JsonObject()
            `object`.addProperty("userId", progress.userId)
            `object`.addProperty("parentCode", progress.parentCode)
            `object`.addProperty("courseId", progress.courseId)
            `object`.addProperty("passed", progress.passed)
            `object`.addProperty("stepNum", progress.stepNum)
            `object`.addProperty("createdOn", progress.createdOn)
            `object`.addProperty("createdDate", progress.createdDate)
            `object`.addProperty("updatedDate", progress.updatedDate)
            return `object`
        }

        @JvmStatic
        fun getCourseProgress(mRealm: Realm, userId: String?): HashMap<String?, JsonObject> {
            val r = getMyCourseByUserId(userId, mRealm.where(RealmMyCourse::class.java).findAll())
            val map = HashMap<String?, JsonObject>()
            for (course in r) {
                val `object` = JsonObject()
                val steps = RealmCourseStep.getSteps(mRealm, course.courseId)
                `object`.addProperty("max", steps.size)
                `object`.addProperty("current", getCurrentProgress(steps, mRealm, userId, course.courseId))
                if (isMyCourse(userId, course.courseId, mRealm)) map[course.courseId] = `object`
            }
            return map
        }

        @JvmStatic
        fun getPassedCourses(mRealm: Realm, userId: String?): List<RealmSubmission> {
            val progresses = mRealm.where(RealmCourseProgress::class.java).equalTo("userId", userId).equalTo("passed", true).findAll()
            val list: MutableList<RealmSubmission> = ArrayList()
            for (progress in progresses) {
                Utilities.log("Course id  certified " + progress.courseId)
                val sub = mRealm.where(RealmSubmission::class.java)
                    .contains("parentId", progress.courseId).equalTo("userId", userId)
                    .sort("lastUpdateTime", Sort.DESCENDING).findFirst()
                if (sub != null) list.add(sub)
            }
            return list
        }

        @JvmStatic
        fun getCurrentProgress(steps: List<RealmCourseStep?>, mRealm: Realm, userId: String?, courseId: String?): Int {
            var i: Int
            i = 0
            while (i < steps.size) {
                val progress = mRealm.where(RealmCourseProgress::class.java).equalTo("stepNum", i + 1).equalTo("userId", userId).equalTo("courseId", courseId)
                    .findFirst()
                    ?: break
                i++
            }
            return i
        }

        fun insert(mRealm: Realm, act: JsonObject?) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            Utilities.log("insert course progresss " + Gson().toJson(act))
            var courseProgress = mRealm.where(RealmCourseProgress::class.java).equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
            if (courseProgress == null) courseProgress = mRealm.createObject(RealmCourseProgress::class.java, JsonUtils.getString("_id", act))
            courseProgress!!.set_rev(JsonUtils.getString("_rev", act))
            courseProgress.set_id(JsonUtils.getString("_id", act))
            courseProgress.passed = JsonUtils.getBoolean("passed", act)
            courseProgress.stepNum = JsonUtils.getInt("stepNum", act)
            courseProgress.userId = JsonUtils.getString("userId", act)
            courseProgress.parentCode = JsonUtils.getString("parentCode", act)
            courseProgress.courseId = JsonUtils.getString("courseId", act)
            courseProgress.createdOn = JsonUtils.getString("createdOn", act)
            courseProgress.createdDate = JsonUtils.getLong("createdDate", act)
            courseProgress.updatedDate = JsonUtils.getLong("updatedDate", act)
            mRealm.commitTransaction()
        }
    }
}
