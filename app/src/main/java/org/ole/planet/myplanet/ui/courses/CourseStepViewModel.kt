package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.ResourceDownloadCoordinator
import org.ole.planet.myplanet.utils.MarkdownUtils
import org.ole.planet.myplanet.utils.UrlUtils

data class CourseStepUiState(
    val step: CourseStep? = null,
    val resources: List<MyLibrary> = emptyList(),
    val stepExams: List<StepExam> = emptyList(),
    val stepSurvey: List<StepExam> = emptyList(),
    val courseTitle: String? = null,
    val userHasCourse: Boolean = false,
    val hasExam: Boolean = false,
    val hasSurvey: Boolean = false,
    val markdownDescription: String = "",
    val user: UserEntity? = null,
    val isDownloadingResources: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class CourseStepViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val userRepository: UserRepository,
    private val resourcesRepository: ResourcesRepository,
    private val configurationsRepository: ConfigurationsRepository,
    private val progressRepository: ProgressRepository,
    private val resourceDownloadCoordinator: ResourceDownloadCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseStepUiState())
    val uiState: StateFlow<CourseStepUiState> = _uiState.asStateFlow()

    private var loadDataJob: Job? = null
    private var saveInProgressJob: Job? = null

    fun loadStep(stepId: String?, stepNumber: Int, nextStepId: String?) {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val user = userRepository.getUserModel()
            val data = coursesRepository.getCourseStepData(stepId ?: "", user?.id)
            val title = data.step.courseId?.let { coursesRepository.getCourseTitleById(it) }

            val baseDirPath = try {
                MainApplication.context.getExternalFilesDir(null)?.toString()
            } catch (e: Exception) {
                null
            }

            val markdownContentWithLocalPaths = MarkdownUtils.prependBaseUrlToImages(
                data.step.description,
                "file://${baseDirPath}/ole/",
                600, 350
            )

            val notDownloaded = data.resources.filter { !it.isResourceOffline() }
            var isDownloading = false
            if (notDownloaded.isNotEmpty()) {
                val serverAvailable = configurationsRepository.checkServerAvailability()
                if (serverAvailable) {
                    isDownloading = true
                    viewModelScope.launch {
                        resourcesRepository.downloadResourcesPriority(notDownloaded)
                    }
                }
            }

            if (nextStepId != null) {
                viewModelScope.launch {
                    val nextResources = resourcesRepository.getAllStepResources(nextStepId)
                    val nextNotDownloaded = nextResources.filter { !it.isResourceOffline() }
                    if (nextNotDownloaded.isNotEmpty()) {
                        val urls = ArrayList(nextNotDownloaded.map { UrlUtils.getUrl(it) })
                        if (urls.isNotEmpty()) {
                            resourceDownloadCoordinator.startBackgroundDownload(urls)
                        }
                    }
                }
            }

            _uiState.value = CourseStepUiState(
                step = data.step,
                resources = data.resources,
                stepExams = data.stepExams,
                stepSurvey = data.stepSurvey,
                courseTitle = title,
                userHasCourse = data.userHasCourse,
                hasExam = data.hasExam,
                hasSurvey = data.hasSurvey,
                markdownDescription = markdownContentWithLocalPaths,
                user = user,
                isDownloadingResources = isDownloading,
                isLoading = false
            )
        }
    }

    fun saveCourseProgress(stepNumber: Int) {
        if (saveInProgressJob?.isActive == true) return
        val state = _uiState.value
        val step = state.step ?: return
        if (!state.userHasCourse) return
        val user = state.user
        val userId = user?.id
        val planetCode = user?.planetCode
        val parentCode = user?.parentCode
        saveInProgressJob = viewModelScope.launch {
            progressRepository.saveCourseProgress(
                userId,
                planetCode,
                parentCode,
                step.courseId,
                stepNumber,
                if (state.stepExams.isEmpty()) true else null
            )
        }
        saveInProgressJob?.invokeOnCompletion { saveInProgressJob = null }
    }

    fun refreshInlineResources(stepId: String?) {
        viewModelScope.launch {
            val updatedResources = resourcesRepository.getAllStepResources(stepId)
            _uiState.update {
                it.copy(
                    resources = updatedResources,
                    isDownloadingResources = false
                )
            }
        }
    }
}
