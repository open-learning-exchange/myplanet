package org.ole.planet.myplanet.base

import com.google.gson.JsonArray
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.Sort
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    var isMyCourseLib: Boolean = false

    @Suppress("UNCHECKED_CAST")
    fun getList(c: Class<*>): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                mRealm.query<RealmStepExam>("type == $0", "surveys").find() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c)
            }
            c == RealmMyLibrary::class.java -> {
                val results = mRealm.query<RealmMyLibrary>("isPrivate == $0", false).find()
                RealmMyLibrary.getOurLibrary(model?.id, results) as List<LI>
            }
            else -> {
                val myLibItems = getMyLibItems(c)
                val results = mRealm.query<RealmMyCourse>()
                    .query("courseTitle != $0", "")
                    .find()
                val ourCourseItems = RealmMyCourse.getOurCourse(model?.id ?: "", results)

                when (c) {
                    RealmMyCourse::class.java -> {
                        val combinedList = mutableListOf<RealmMyCourse>()
                        (myLibItems as List<RealmMyCourse>).forEach { course ->
                            course.isMyCourse = true
                            combinedList.add(course)
                        }
                        ourCourseItems.forEach { course ->
                            if (!combinedList.any { it.id == course.id }) {
                                course.isMyCourse = false
                                combinedList.add(course)
                            }
                        }
                        combinedList as List<LI>
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getList(c: Class<*>, orderBy: String? = null, sort: Sort = Sort.ASCENDING): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                mRealm.query<RealmStepExam>("type == $0", "surveys").apply {
                    orderBy?.let { sort(it, sort) }
                }.find() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c, orderBy)
            }
            c == RealmMyLibrary::class.java -> {
                val results = mRealm.query<RealmMyLibrary>("isPrivate == $0", false).apply {
                    orderBy?.let { sort(it, sort) }
                }.find()
                RealmMyLibrary.getOurLibrary(model?.id, results) as List<LI>
            }
            else -> {
                val results = mRealm.query<RealmMyCourse>().apply {
                    orderBy?.let { sort(it, sort) }
                }.find()
                RealmMyCourse.getOurCourse(model?.id ?: "", results) as List<LI>
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getMyLibItems(c: Class<*>, orderBy: String? = null): List<LI> {
        return when (c) {
            RealmMyLibrary::class.java -> {
                val results = mRealm.query<RealmMyLibrary>().apply {
                    orderBy?.let { sort(it) }
                }.find()
                RealmMyLibrary.getMyLibraryByUserId(model?.id, results) as List<LI>
            }
            RealmMyCourse::class.java -> {
                val results = mRealm.query<RealmMyCourse>().apply {
                    orderBy?.let { sort(it) }
                }.find()
                RealmMyCourse.getMyCourseByUserId(model?.id ?: "", results) as List<LI>
            }
            else -> throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
        }
    }

    fun getJsonArrayFromList(list: Set<String>): JsonArray {
        return JsonArray().apply {
            list.forEach { add(it) }
        }
    }

    companion object {
        var isSurvey: Boolean = false
    }
}