package org.ole.planet.myplanet.base

import com.google.gson.JsonArray
import io.realm.RealmModel
import io.realm.Sort
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    var isMyCourseLib: Boolean = false

    @Suppress("UNCHECKED_CAST")
    fun getList(c: Class<*>): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                mRealm.where(c).equalTo("type", "surveys").findAll().toList() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>)
            }
            c == RealmMyLibrary::class.java -> {
                RealmMyLibrary.getOurLibrary(model?.id, mRealm.where(c).equalTo("isPrivate", false).findAll().toList()) as List<LI>
            }
            else -> {
                val results: List<RealmMyCourse> = mRealm.where(RealmMyCourse::class.java).isNotEmpty("courseTitle").findAll().toList()
                val filteredResults = RealmMyCourse.getOurCourse(model?.id, results)
                filteredResults as List<LI>
            }
        }
    }

    fun getList(c: Class<*>, orderBy: String): List<LI> = getList(c, orderBy, Sort.ASCENDING)

    @Suppress("UNCHECKED_CAST")
    fun getList(c: Class<*>, orderBy: String? = null, sort: Sort = Sort.ASCENDING): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                mRealm.where(c).equalTo("type", "surveys").sort(orderBy ?: "", sort).findAll().toList() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>, orderBy)
            }
            c == RealmMyLibrary::class.java -> {
                RealmMyLibrary.getOurLibrary(model?.id, mRealm.where(c).equalTo("isPrivate", false).sort(orderBy ?: "", sort).findAll().toList()) as List<LI>
            }
            else -> {
                val results = mRealm.where(RealmMyCourse::class.java).sort(orderBy ?: "", sort).findAll().toList() as List<RealmMyCourse>
                RealmMyCourse.getOurCourse(model?.id, results) as List<LI>
            }
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T : RealmModel> getMyLibItems(c: Class<T>, orderBy: String? = null): List<LI> {
        val query = mRealm.where(c)
        val realmResults = if (orderBy != null) {
            query.sort(orderBy).findAll()
        } else {
            query.findAll()
        }
        val results: List<T> = realmResults.toList()
        return when (c) {
            RealmMyLibrary::class.java -> {
                RealmMyLibrary.getMyLibraryByUserId(model?.id, results as? List<RealmMyLibrary> ?: emptyList()) as List<LI>
            }
            RealmMyCourse::class.java -> {
                RealmMyCourse.getMyCourseByUserId(model?.id, results as? List<RealmMyCourse> ?: emptyList()) as List<LI>
            }
            else -> throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
        }
    }

    fun getJsonArrayFromList(list: Set<String>): JsonArray {
        val array = JsonArray()
        list.forEach { array.add(it) }
        return array
    }

    companion object {
        var isSurvey: Boolean = false
    }
}
