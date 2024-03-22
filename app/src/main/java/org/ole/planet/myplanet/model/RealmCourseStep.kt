package org.ole.planet.myplanet.model

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.regex.Pattern

open class RealmCourseStep : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var courseId: String? = null
    @JvmField
    var stepTitle: String? = null
    @JvmField
    var description: String? = null
    var noOfResources: Int? = null
        private set
    var noOfExams: Int? = null
        private set

    fun setNoOfResources(noOfResources: Int) {
        this.noOfResources = noOfResources
    }

    fun setNoOfExams(noOfExams: Int) {
        this.noOfExams = noOfExams
    }

    companion object {
        @JvmStatic
        fun insertCourseSteps(myCoursesID: String?, steps: JsonArray, numberOfSteps: Int, mRealm: Realm) {
            for (step in 0 until numberOfSteps) {
                val step_id = Base64.encodeToString(steps[step].toString().toByteArray(), Base64.NO_WRAP)
                var myCourseStepDB = mRealm.where(RealmCourseStep::class.java).equalTo("id", step_id).findFirst()
                if (myCourseStepDB == null) {
                    myCourseStepDB = mRealm.createObject(RealmCourseStep::class.java, step_id)
                }
                myCourseStepDB?.courseId = myCoursesID
                val stepContainer = steps[step].asJsonObject
                myCourseStepDB?.stepTitle = JsonUtils.getString("stepTitle", stepContainer)
                myCourseStepDB?.description = JsonUtils.getString("description", stepContainer)
                val description = JsonUtils.getString("description", stepContainer)
                val links = extractLinks(description)
                val concatenatedLinks = ArrayList<String>()
                val baseUrl = Utilities.getUrl()
                for (link in links) {
                    val concatenatedLink = "$baseUrl/$link"
                    concatenatedLinks.add(concatenatedLink)
                }
                Utilities.openDownloadService(MainApplication.context, concatenatedLinks)
                myCourseStepDB?.setNoOfResources(JsonUtils.getJsonArray("resources", stepContainer).size())
                insertCourseStepsAttachments(myCoursesID, step_id, JsonUtils.getJsonArray("resources", stepContainer), mRealm)
                insertExam(stepContainer, mRealm, step_id, step + 1, myCoursesID)
            }
        }

        @JvmStatic
        fun extractLinks(text: String?): ArrayList<String> {
            val links = ArrayList<String>()
            val pattern = Pattern.compile("!\\[.*?\\]\\((.*?)\\)")
            val matcher = text?.let { pattern.matcher(it) }
            if (matcher != null) {
                while (matcher.find()) {
                    matcher.group(1)?.let { links.add(it) }
                }
            }
            return links
        }

        private fun insertExam(stepContainer: JsonObject, mRealm: Realm, step_id: String, i: Int, myCoursesID: String?) {
            if (stepContainer.has("exam")) {
                val `object` = stepContainer.getAsJsonObject("exam")
                `object`.addProperty("stepNumber", i)
                RealmStepExam.insertCourseStepsExams(myCoursesID, step_id, `object`, mRealm)
            }
        }

        @JvmStatic
        fun getStepIds(mRealm: Realm, courseId: String?): Array<String?> {
            val list: List<RealmCourseStep> = mRealm.where(RealmCourseStep::class.java).equalTo("courseId", courseId).findAll()
            val myIds = arrayOfNulls<String>(list.size)
            var i = 0
            for (c in list) {
                myIds[i] = c.id
                i++
            }
            return myIds
        }

        @JvmStatic
        fun getSteps(mRealm: Realm, courseId: String?): List<RealmCourseStep> {
            return mRealm.where(RealmCourseStep::class.java).equalTo("courseId", courseId).findAll()
        }

        fun insertCourseStepsAttachments(myCoursesID: String?, stepId: String?, resources: JsonArray, mRealm: Realm?) {
            for (i in 0 until resources.size()) {
                if (mRealm != null) {
                    RealmMyLibrary.createStepResource(mRealm, resources[i].asJsonObject, myCoursesID, stepId)
                }
            }
        }
    }
}