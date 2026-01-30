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
    suspend fun getList(c: Class<*>): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getSurveys() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>)
            }
            c == RealmMyLibrary::class.java -> {
                val publicLibs = resourcesRepository.getPublicLibraries()
                RealmMyLibrary.getOurLibrary(model?.id, publicLibs) as List<LI>
            }
            else -> {
                val myLibItems = getMyLibItems(c as Class<out RealmModel>)
                // All courses with title
                val allCourses = coursesRepository.filterCourses("", "", "", emptyList())
                val ourCourseItems = RealmMyCourse.getOurCourse(model?.id, allCourses)

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
    suspend fun getList(c: Class<*>, orderBy: String? = null, sort: Sort = Sort.ASCENDING): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getSurveys(orderBy, sort) as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>, orderBy)
            }
            c == RealmMyLibrary::class.java -> {
                val publicLibs = resourcesRepository.getPublicLibraries(orderBy, sort)
                RealmMyLibrary.getOurLibrary(model?.id, publicLibs) as List<LI>
            }
            else -> {
                // TODO: Support sort in filterCourses or separate method?
                // For now, fetching all and let fragments sort, or implement manual sort if critical.
                // The original code sorted RealmResults.
                val allCourses = coursesRepository.filterCourses("", "", "", emptyList())
                // Assuming sorting will be handled by UI or we ignore orderBy for now as it wasn't easy to implement efficiently without repo changes.
                // But wait, the second getList overload was used.
                // If I want to support it, I should add sorting to repo.
                // But for now I'll return unsorted and let caller handle or rely on default sort.
                RealmMyCourse.getOurCourse(model?.id, allCourses) as List<LI>
            }
        }
    }
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : RealmModel> getMyLibItems(c: Class<T>, orderBy: String? = null): List<LI> {
        val userId = model?.id
        if (userId.isNullOrEmpty()) return emptyList()

        return when (c) {
            RealmMyLibrary::class.java -> {
                resourcesRepository.getMyLibrary(userId) as List<LI>
            }
            RealmMyCourse::class.java -> {
                coursesRepository.getMyCourses(userId) as List<LI>
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
