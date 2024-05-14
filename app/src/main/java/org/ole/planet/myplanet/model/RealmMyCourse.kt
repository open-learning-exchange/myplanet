package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities

open class RealmMyCourse : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    var userId: RealmList<String>? = null
        private set
    @JvmField
    var courseId: String? = null
    @JvmField
    var course_rev: String? = null
    @JvmField
    var languageOfInstruction: String? = null
    @JvmField
    var courseTitle: String? = null
    @JvmField
    var memberLimit: Int? = null
    @JvmField
    var description: String? = null
    @JvmField
    var method: String? = null
    @JvmField
    var gradeLevel: String? = null
    @JvmField
    var subjectLevel: String? = null
    @JvmField
    var createdDate: Long = 0
    private var numberOfSteps: Int? = null
    fun setUserId(userId: String?) {
        if (this.userId == null) {
            this.userId = RealmList()
        }
        if (!this.userId?.contains(userId)!! && !TextUtils.isEmpty(userId)) {
            this.userId?.add(userId)
        }
    }

    fun removeUserId(userId: String?) {
        this.userId?.remove(userId)
    }

    fun getnumberOfSteps(): Int {
        return numberOfSteps ?: 0
    }

    fun setnumberOfSteps(numberOfSteps: Int?) {
        this.numberOfSteps = numberOfSteps
    }

    override fun toString(): String {
        return courseTitle ?: ""
    }

    companion object {
        @JvmStatic
        fun insertMyCourses(userId: String?, myCousesDoc: JsonObject?, mRealm: Realm) {
            val id = JsonUtils.getString("_id", myCousesDoc)
//            mRealm.executeTransaction { realm ->
                var myMyCoursesDB = mRealm.where(RealmMyCourse::class.java).equalTo("id", id).findFirst()
                if (myMyCoursesDB == null) {
                    myMyCoursesDB = mRealm.createObject(RealmMyCourse::class.java, id)
                }
                myMyCoursesDB?.setUserId(userId)
                myMyCoursesDB?.courseId = JsonUtils.getString("_id", myCousesDoc)
                myMyCoursesDB?.course_rev = JsonUtils.getString("_rev", myCousesDoc)
                myMyCoursesDB?.languageOfInstruction = JsonUtils.getString("languageOfInstruction", myCousesDoc)
                myMyCoursesDB?.courseTitle = JsonUtils.getString("courseTitle", myCousesDoc)
                myMyCoursesDB?.memberLimit = JsonUtils.getInt("memberLimit", myCousesDoc)
                myMyCoursesDB?.description = JsonUtils.getString("description", myCousesDoc)
                val description = JsonUtils.getString("description", myCousesDoc)
                val links = RealmCourseStep.extractLinks(description)
                val concatenatedLinks = ArrayList<String>()
                val baseUrl = Utilities.getUrl()
                for (link in links) {
                    val concatenatedLink = "$baseUrl/$link"
                    concatenatedLinks.add(concatenatedLink)
                }
                Utilities.openDownloadService(MainApplication.context, concatenatedLinks)
                myMyCoursesDB?.method = JsonUtils.getString("method", myCousesDoc)
                myMyCoursesDB?.gradeLevel = JsonUtils.getString("gradeLevel", myCousesDoc)
                myMyCoursesDB?.subjectLevel = JsonUtils.getString("subjectLevel", myCousesDoc)
                myMyCoursesDB?.createdDate = JsonUtils.getLong("createdDate", myCousesDoc)
                myMyCoursesDB?.setnumberOfSteps(JsonUtils.getJsonArray("steps", myCousesDoc).size())
                RealmCourseStep.insertCourseSteps(
                    myMyCoursesDB?.courseId,
                    JsonUtils.getJsonArray("steps", myCousesDoc),
                    JsonUtils.getJsonArray("steps", myCousesDoc).size(),
                    mRealm
                )
//            }
        }


        @JvmStatic
        fun getMyByUserId(mRealm: Realm, settings: SharedPreferences?): List<RealmObject> {
            val libs = mRealm.where(RealmMyCourse::class.java).findAll()
            val libraries: MutableList<RealmObject> = ArrayList()
            for (item in libs) {
                if (item.userId?.contains(settings?.getString("userId", "--")) == true) {
                    libraries.add(item)
                }
            }
            return libraries
        }

        @JvmStatic
        fun getMyCourseByUserId(userId: String?, libs: List<RealmMyCourse>?): List<RealmMyCourse> {
            val libraries: MutableList<RealmMyCourse> = ArrayList()
            for (item in libs ?: emptyList()) {
                if (item.userId?.contains(userId) == true) {
                    libraries.add(item)
                }
            }
            return libraries
        }

        @JvmStatic
        fun getOurCourse(userId: String?, libs: List<RealmMyCourse>): List<RealmMyCourse> {
            val libraries: MutableList<RealmMyCourse> = ArrayList()
            for (item in libs) {
                if (!item.userId?.contains(userId)!!) {
                    libraries.add(item)
                }
            }
            return libraries
        }

        @JvmStatic
        fun isMyCourse(userId: String?, courseId: String?, realm: Realm): Boolean {
            return getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findAll()).isNotEmpty()
        }

        @JvmStatic
        fun getCourseByCourseId(courseId: String, mRealm: Realm): RealmMyCourse? {
            return mRealm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
        }

        @JvmStatic
        fun insert(mRealm: Realm, doc: JsonObject?) {
            insertMyCourses("", doc, mRealm)
        }

        @JvmStatic
        fun getMyCourse(mRealm: Realm, id: String?): RealmMyCourse? {
            return mRealm.where(RealmMyCourse::class.java).equalTo("courseId", id).findFirst()
        }

        @JvmStatic
        fun createMyCourse(course: RealmMyCourse?, mRealm: Realm, id: String?) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            course?.setUserId(id)
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun getMyCourseIds(realm: Realm?, userId: String?): JsonArray {
            val myCourses = getMyCourseByUserId(userId, realm?.where(RealmMyCourse::class.java)?.findAll())
            val ids = JsonArray()
            for (lib in myCourses) {
                ids.add(lib.courseId)
            }
            return ids
        }
    }
}