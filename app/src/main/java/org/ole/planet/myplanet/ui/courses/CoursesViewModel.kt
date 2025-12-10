package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.getCourseProgress
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatings
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val databaseService: DatabaseService,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val courseRepository: CourseRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _courses = MutableStateFlow<List<RealmMyCourse>>(emptyList())
    val courses: StateFlow<List<RealmMyCourse>> = _courses.asStateFlow()

    private val _ratings = MutableStateFlow<Map<String, JsonObject>>(emptyMap())
    val ratings: StateFlow<Map<String, JsonObject>> = _ratings.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, JsonObject>>(emptyMap())
    val progress: StateFlow<Map<String, JsonObject>> = _progress.asStateFlow()

    private val _resources = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val resources: StateFlow<List<RealmMyLibrary>> = _resources.asStateFlow()

    private val _currentFilter = MutableStateFlow(FilterState())

    data class FilterState(
        val query: String = "",
        val tags: List<RealmTag> = emptyList(),
        val gradeLevel: String = "",
        val subjectLevel: String = "",
        val isMyCourseLib: Boolean = false
    )

    fun setMyCourseLib(isMyCourseLib: Boolean) {
        val currentState = _currentFilter.value
        if (currentState.isMyCourseLib != isMyCourseLib) {
             _currentFilter.value = currentState.copy(isMyCourseLib = isMyCourseLib)
            loadCourses()
        }
    }

    fun filter(query: String? = null, tags: List<RealmTag>? = null, grade: String? = null, subject: String? = null) {
        val currentState = _currentFilter.value
        val newState = currentState.copy(
            query = query ?: currentState.query,
            tags = tags ?: currentState.tags,
            gradeLevel = grade ?: currentState.gradeLevel,
            subjectLevel = subject ?: currentState.subjectLevel
        )
        if (newState != currentState) {
            _currentFilter.value = newState
            loadCourses()
        }
    }

    fun loadCourses() {
        viewModelScope.launch {
            val filter = _currentFilter.value
            val userId = userProfileDbHandler.userModel?.id

            val result = withContext(Dispatchers.IO) {
                databaseService.withRealm { realm ->
                    val ratingsMap: HashMap<String?, JsonObject> = getRatings(realm, "course", userId)
                    val progressMap: HashMap<String?, JsonObject> = getCourseProgress(realm, userId)

                    var query = realm.where(RealmMyCourse::class.java)

                    if (filter.query.isNotEmpty()) {
                        query = query.contains("courseTitle", filter.query, io.realm.Case.INSENSITIVE)
                    }

                    if (filter.gradeLevel.isNotEmpty()) {
                        query = query.equalTo("gradeLevel", filter.gradeLevel)
                    }
                    if (filter.subjectLevel.isNotEmpty()) {
                        query = query.equalTo("subjectLevel", filter.subjectLevel)
                    }

                    var results = query.findAll()

                    if (filter.tags.isNotEmpty()) {
                         val taggedCourseIds = mutableSetOf<String>()
                         filter.tags.forEach { tag ->
                             val links = realm.where(RealmTag::class.java)
                                 .equalTo("db", "courses")
                                 .equalTo("tagId", tag.id)
                                 .findAll()
                             taggedCourseIds.addAll(links.mapNotNull { it.linkId })
                         }
                         results = results.where().`in`("courseId", taggedCourseIds.toTypedArray()).findAll()
                    }

                    val list = realm.copyFromRealm(results)

                    val filteredList = if (filter.isMyCourseLib) {
                        RealmMyCourse.getMyCourseByUserId(userId, list)
                    } else {
                        RealmMyCourse.getAllCourses(userId, list)
                    }

                    val resources = if (filter.isMyCourseLib) {
                        val courseIds = filteredList.mapNotNull { it.id }
                        realm.where(RealmMyLibrary::class.java)
                            .`in`("courseId", courseIds.toTypedArray())
                            .equalTo("resourceOffline", false)
                            .isNotNull("resourceLocalAddress")
                            .findAll()
                            .let { realm.copyFromRealm(it) }
                    } else {
                        emptyList()
                    }

                    Triple(filteredList, Pair(ratingsMap, progressMap), resources)
                }
            }

            var coursesList = result.first
            coursesList = coursesList.filter { !it.courseTitle.isNullOrBlank() }
            coursesList = coursesList.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))

            val maps = result.second
            _ratings.value = maps.first.filterKeys { it != null } as Map<String, JsonObject>
            _progress.value = maps.second.filterKeys { it != null } as Map<String, JsonObject>
            _courses.value = coursesList
            _resources.value = result.third
        }
    }

    fun addToMyList(selectedItems: List<RealmMyCourse>): Boolean {
        if (selectedItems.isEmpty()) return false

        val userId = userProfileDbHandler.userModel?.id ?: return false
        val resourceIds = mutableSetOf<String>()
        val courseIds = mutableSetOf<String>()

        selectedItems.forEach { item ->
            item.courseId?.let { courseIds.add(it) }
        }

        if (courseIds.isEmpty()) return false

        viewModelScope.launch {
            courseIds.forEach { courseId ->
                courseRepository.markCourseAdded(courseId, userId)
            }
            loadCourses()
        }
        return true
    }

    fun deleteSelected(selectedItems: List<RealmMyCourse>, deleteProgress: Boolean) {
        if (selectedItems.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
             databaseService.executeTransactionAsync { realm ->
                selectedItems.forEach { item ->
                     val dbItem = realm.where(RealmMyCourse::class.java).equalTo("id", item.id).findFirst()
                     dbItem?.let {
                         it.removeUserId(userProfileDbHandler.userModel?.id)
                         if (deleteProgress) {
                             realm.where(RealmCourseProgress::class.java).equalTo("courseId", it.courseId).findAll().deleteAllFromRealm()
                             val examList = realm.where(RealmStepExam::class.java).equalTo("courseId", it.courseId).findAll()
                             for (exam in examList) {
                                 realm.where(RealmSubmission::class.java).equalTo("parentId", exam.id)
                                     .notEqualTo("type", "survey").equalTo("uploaded", false).findAll()
                                     .deleteAllFromRealm()
                             }
                         }
                     }
                }
             }
             withContext(Dispatchers.Main) {
                 loadCourses()
             }
        }
    }
}
