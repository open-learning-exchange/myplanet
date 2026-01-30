package org.ole.planet.myplanet.base

import com.google.gson.JsonArray
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.repository.SurveysRepository

abstract class BaseRecyclerParentFragment<LI> : BaseResourceFragment() {
    @Inject
    lateinit var surveysRepository: SurveysRepository
    var isMyCourseLib: Boolean = false

    @Suppress("UNCHECKED_CAST")
    suspend fun getList(c: Class<*>, orderBy: String? = null, sort: Sort = Sort.ASCENDING): List<LI> {
        return when {
            c == RealmStepExam::class.java -> {
                surveysRepository.getSurveys(orderBy, sort) as List<LI>
            }
            isMyCourseLib -> {
                when (c) {
                    RealmMyLibrary::class.java -> resourcesRepository.getMyLibItems(model?.id, orderBy, sort) as List<LI>
                    RealmMyCourse::class.java -> coursesRepository.getMyCourseItems(model?.id, orderBy, sort) as List<LI>
                    else -> throw IllegalArgumentException("Unsupported class: ${c.simpleName}")
                }
            }
            c == RealmMyLibrary::class.java -> {
                resourcesRepository.getPublicLibrary(model?.id, orderBy, sort) as List<LI>
            }
            c == RealmMyCourse::class.java -> {
                coursesRepository.getAllCourses(model?.id, orderBy, sort) as List<LI>
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
