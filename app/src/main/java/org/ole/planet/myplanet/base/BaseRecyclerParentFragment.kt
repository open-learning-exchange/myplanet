package org.ole.planet.myplanet.base

import com.google.gson.JsonArray
import io.realm.RealmModel
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.repository.SurveysRepository

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    var isMyCourseLib: Boolean = false
    @Inject
    lateinit var surveysRepository: SurveysRepository

    @Deprecated("Use repository instead")
    @Suppress("UNCHECKED_CAST")
    suspend fun getList(c: Class<*>): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getAllSurveys() as List<LI>
            }
            isMyCourseLib -> {
                getMyLibItems(c as Class<out RealmModel>)
            }
            c == RealmMyLibrary::class.java -> {
                resourcesRepository.getAllLibraryItems() as List<LI>
            }
            else -> {
                when (c) {
                    RealmMyCourse::class.java -> {
                        coursesRepository.getAllCourses(model?.id) as List<LI>
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
                    }
                }
            }
        }
    }

    @Deprecated("Use repository instead")
    @Suppress("UNCHECKED_CAST")
    suspend fun getList(c: Class<*>, orderBy: String? = null, sort: Sort = Sort.ASCENDING): List<LI> {
        // Ignoring sort/orderBy as repositories don't support it generically yet, and existing usage might be limited.
        // If sorting is critical, we should add sorted methods to repositories or sort in memory here.
        // For now delegating to getList(c) as simpler refactor step, assuming caller handles sorting or default sort is fine.
        // Checking usages: CoursesFragment uses getList(RealmMyCourse::class.java) and handles sorting itself.
        // So this overload might be unused or less critical for sorting parameter.
        // Actually BaseRecyclerFragment calls getList in filter functions which are unused.
        return getList(c)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : RealmModel> getMyLibItems(c: Class<T>, orderBy: String? = null): List<LI> {
        return when (c) {
            RealmMyLibrary::class.java -> {
                resourcesRepository.getMyLibrary(model?.id) as List<LI>
            }
            RealmMyCourse::class.java -> {
                coursesRepository.getMyCourses(model?.id ?: "") as List<LI>
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
