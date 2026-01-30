package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentCourseStepBinding
import org.ole.planet.myplanet.model.CourseStepData
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.ui.components.CustomClickableSpan
import org.ole.planet.myplanet.ui.exam.ExamTakingFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.utils.CameraUtils
import org.ole.planet.myplanet.utils.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utils.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utils.MarkdownUtils.prependBaseUrlToImages
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText

@AndroidEntryPoint
class CourseStepFragment : BaseContainerFragment(), ImageCaptureCallback {
    @Inject
    lateinit var progressRepository: ProgressRepository
    private lateinit var fragmentCourseStepBinding: FragmentCourseStepBinding
    var stepId: String? = null
    private lateinit var step: RealmCourseStep
    private lateinit var resources: List<RealmMyLibrary>
    private lateinit var stepExams: List<RealmStepExam>
    private lateinit var stepSurvey: List<RealmStepExam>
    var user: RealmUser? = null
    private var stepNumber = 0
    private var saveInProgress: Job? = null
    private var loadDataJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            stepId = requireArguments().getString("stepId")
            stepNumber = requireArguments().getInt("stepNumber")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCourseStepBinding = FragmentCourseStepBinding.inflate(inflater, container, false)
        user = profileDbHandler.userModel
        fragmentCourseStepBinding.btnTakeTest.visibility = View.VISIBLE
        fragmentCourseStepBinding.btnTakeSurvey.visibility = View.VISIBLE
        return fragmentCourseStepBinding.root
    }

    private fun launchSaveCourseProgress() {
        if (saveInProgress?.isActive == true) return
        val userId = user?.id
        val planetCode = user?.planetCode
        val parentCode = user?.parentCode
        saveInProgress = lifecycleScope.launch {
            progressRepository.saveCourseProgress(
                userId,
                planetCode,
                parentCode,
                step.courseId,
                stepNumber,
                if (stepExams.isEmpty()) true else null
            )
        }
        saveInProgress?.invokeOnCompletion { saveInProgress = null }
    }

    private suspend fun loadStepData(): CourseStepData {
        return coursesRepository.getCourseStepData(stepId!!)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDataJob = viewLifecycleOwner.lifecycleScope.launch {
            val data = loadStepData()
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                step = data.step
                resources = data.resources
                stepExams = data.stepExams
                stepSurvey = data.stepSurvey

                val userHasCourse = coursesRepository.isMyCourse(user?.id, step.courseId)

                fragmentCourseStepBinding.btnResources.text =
                    getString(R.string.resources_size, resources.size)
                hideTestIfNoQuestion()
                fragmentCourseStepBinding.tvTitle.text = step.stepTitle
                val markdownContentWithLocalPaths = prependBaseUrlToImages(
                    step.description,
                    "file://${MainApplication.context.getExternalFilesDir(null)}/ole/",
                    600,
                    350
                )
                setMarkdownText(
                    fragmentCourseStepBinding.description,
                    markdownContentWithLocalPaths
                )
                fragmentCourseStepBinding.description.movementMethod =
                    LinkMovementMethod.getInstance()

                if (!userHasCourse) {
                    fragmentCourseStepBinding.btnTakeTest.visibility = View.GONE
                    fragmentCourseStepBinding.btnTakeSurvey.visibility = View.GONE
                }

                setListeners()
                val textWithSpans = fragmentCourseStepBinding.description.text
                if (textWithSpans is Spannable) {
                    val urlSpans =
                        textWithSpans.getSpans(0, textWithSpans.length, URLSpan::class.java)
                    for (urlSpan in urlSpans) {
                        val start = textWithSpans.getSpanStart(urlSpan)
                        val end = textWithSpans.getSpanEnd(urlSpan)
                        val dynamicTitle = textWithSpans.subSequence(start, end).toString()
                        textWithSpans.setSpan(
                            CustomClickableSpan(
                                urlSpan.url,
                                dynamicTitle,
                                requireActivity()
                            ), start, end, textWithSpans.getSpanFlags(urlSpan)
                        )
                        textWithSpans.removeSpan(urlSpan)
                    }
                }
                if (isVisible && userHasCourse) {
                    launchSaveCourseProgress()
                }
            }
        }
    }

    private fun hideTestIfNoQuestion() {
        fragmentCourseStepBinding.btnTakeTest.visibility = View.GONE
        fragmentCourseStepBinding.btnTakeSurvey.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            if (stepExams.isNotEmpty()) {
                val firstStepId = stepExams[0].id
                val isTestPresent = submissionsRepository.hasSubmission(firstStepId, step.courseId, user?.id, "exam")
                fragmentCourseStepBinding.btnTakeTest.text = if (isTestPresent) {
                    getString(R.string.retake_test, stepExams.size)
                } else {
                    getString(R.string.take_test, stepExams.size)
                }
                fragmentCourseStepBinding.btnTakeTest.visibility = View.VISIBLE
            }
            if (stepSurvey.isNotEmpty()) {
                val firstStepId = stepSurvey[0].id
                val isSurveyPresent = submissionsRepository.hasSubmission(firstStepId, step.courseId, user?.id, "survey")
                fragmentCourseStepBinding.btnTakeSurvey.text = if (isSurveyPresent) {
                    getString(R.string.redo_survey)
                } else {
                    getString(R.string.record_survey)
                }
                fragmentCourseStepBinding.btnTakeSurvey.visibility = View.VISIBLE
            }
        }
    }

    override fun setMenuVisibility(visible: Boolean) {
        super.setMenuVisibility(visible)
        if (!isAdded || !::step.isInitialized) return
        lifecycleScope.launch {
            try {
                if (visible) {
                    val userHasCourse = coursesRepository.isMyCourse(user?.id, step.courseId)
                    if (userHasCourse) {
                        launchSaveCourseProgress()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            val notDownloadedResources = resourcesRepository.getStepResources(stepId, resourceOffline = false)
            setResourceButton(notDownloadedResources, fragmentCourseStepBinding.btnResources)
            val downloadedResources = resourcesRepository.getStepResources(stepId, resourceOffline = true)
            setOpenResourceButton(downloadedResources, fragmentCourseStepBinding.btnOpen)
        }
        fragmentCourseStepBinding.btnTakeTest.setOnClickListener {
            if (stepExams.isNotEmpty()) {
                val takeExam: Fragment = ExamTakingFragment()
                val b = Bundle()
                b.putString("stepId", stepId)
                b.putInt("stepNum", stepNumber)
                takeExam.arguments = b
                homeItemClickListener?.openCallFragment(takeExam)
                capturePhoto(this)
            }
        }

        fragmentCourseStepBinding.btnTakeSurvey.setOnClickListener {
            if (stepSurvey.isNotEmpty()) {
                SubmissionsAdapter.openSurvey(homeItemClickListener, stepSurvey[0].id, false, false, "")
            }
        }
        fragmentCourseStepBinding.btnResources.visibility = View.GONE
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        setListeners()
    }

    override fun onImageCapture(fileUri: String?) {}

    override fun onDestroyView() {
        super.onDestroyView()
        loadDataJob?.cancel()
        CameraUtils.release()
    }
}
