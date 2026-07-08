package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.recyclerview.widget.LinearLayoutManager
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
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.services.ResourceDownloadCoordinator
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.components.CustomClickableSpan
import org.ole.planet.myplanet.ui.exam.ExamTakingFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.utils.CameraUtils
import org.ole.planet.myplanet.utils.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utils.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.MarkdownUtils.prependBaseUrlToImages
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText
import org.ole.planet.myplanet.utils.UrlUtils

@AndroidEntryPoint
class CourseStepFragment : BaseContainerFragment(), ImageCaptureCallback {
    @Inject
    lateinit var configurationsRepository: ConfigurationsRepository
    @Inject
    lateinit var progressRepository: ProgressRepository
    @Inject
    lateinit var resourceDownloadCoordinator: ResourceDownloadCoordinator
    @Inject
    lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var fragmentCourseStepBinding: FragmentCourseStepBinding
    var stepId: String? = null
    private var nextStepId: String? = null
    private lateinit var step: RealmCourseStep
    private lateinit var resources: List<RealmMyLibrary>
    private lateinit var stepExams: List<RealmStepExam>
    private lateinit var stepSurvey: List<RealmStepExam>
    var user: RealmUser? = null
    private var stepNumber = 0
    private var courseTitle: String? = null
    private var saveInProgress: Job? = null
    private var loadDataJob: Job? = null
    private var inlineResourceAdapter: InlineResourceAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            stepId = requireArguments().getString("stepId")
            stepNumber = requireArguments().getInt("stepNumber")
            nextStepId = requireArguments().getString("nextStepId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCourseStepBinding = FragmentCourseStepBinding.inflate(inflater, container, false)
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
        return coursesRepository.getCourseStepData(stepId ?: "", user?.id)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDataJob = viewLifecycleOwner.lifecycleScope.launch {
            user = profileDbHandler.getUserModel()
            val data = loadStepData()
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                step = data.step
                resources = data.resources
                stepExams = data.stepExams
                stepSurvey = data.stepSurvey
                courseTitle = step.courseId?.let { coursesRepository.getCourseTitleById(it) }

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
                fragmentCourseStepBinding.description.setTextIsSelectable(true)
                fragmentCourseStepBinding.description.customSelectionActionModeCallback =
                    createAiSelectionCallback()

                if (!data.userHasCourse) {
                    fragmentCourseStepBinding.btnTakeTest.visibility = View.GONE
                    fragmentCourseStepBinding.btnTakeSurvey.visibility = View.GONE
                }

                fragmentCourseStepBinding.btnAskAi.visibility = View.VISIBLE
                setListeners()
                setupInlineResources()
                autoDownloadResources()
                prefetchNextStepResources()

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
                if (data.userHasCourse) {
                    viewLifecycleOwner.lifecycle.withResumed {
                        launchSaveCourseProgress()
                    }
                }
            }
        }
    }

    private fun setupInlineResources() {
        if (resources.isEmpty()) {
            fragmentCourseStepBinding.tvResourcesHeader.visibility = View.GONE
            fragmentCourseStepBinding.rvInlineResources.visibility = View.GONE
            return
        }

        fragmentCourseStepBinding.tvResourcesHeader.visibility = View.VISIBLE
        fragmentCourseStepBinding.rvInlineResources.visibility = View.VISIBLE

        inlineResourceAdapter = InlineResourceAdapter(
            dispatcherProvider,
            { holder, resource, file ->
                holder.setPreviewJob(viewLifecycleOwner.lifecycleScope.launch(dispatcherProvider.main) {
                    inlineResourceAdapter?.loadPreview(holder, resource, file)
                })
            },
            { library ->
                openResource(library)
            }
        )
        fragmentCourseStepBinding.rvInlineResources.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = inlineResourceAdapter
        }
        inlineResourceAdapter?.submitList(resources)
    }

    private fun autoDownloadResources() {
        val notDownloaded = resources.filter { !it.isResourceOffline() }
        if (notDownloaded.isEmpty()) {
            return
        }

        fragmentCourseStepBinding.resourceDownloadProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val serverAvailable = configurationsRepository.checkServerAvailability()

            if (serverAvailable) {
                resourcesRepository.downloadResourcesPriority(notDownloaded)
            } else {
                fragmentCourseStepBinding.resourceDownloadProgress.visibility = View.GONE
            }
        }
    }

    private fun prefetchNextStepResources() {
        if (nextStepId == null) {
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val nextResources = resourcesRepository.getAllStepResources(nextStepId)
            val notDownloaded = nextResources.filter { !it.isResourceOffline() }
            if (notDownloaded.isNotEmpty()) {
                val urls = ArrayList(notDownloaded.map { UrlUtils.getUrl(it) })
                if (urls.isNotEmpty()) {
		    resourceDownloadCoordinator.startBackgroundDownload(urls)
                }
            }
        }
    }

    private fun refreshInlineResources() {
        viewLifecycleOwner.lifecycleScope.launch {
            val updatedResources = resourcesRepository.getAllStepResources(stepId)
            resources = updatedResources
            inlineResourceAdapter?.submitList(updatedResources)
            fragmentCourseStepBinding.resourceDownloadProgress.visibility = View.GONE
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
        fragmentCourseStepBinding.btnTakeTest.setOnClickListener {
            if (stepExams.isNotEmpty()) {
                val takeExam: Fragment = ExamTakingFragment()
                val b = Bundle()
                b.putString("stepId", stepId)
                b.putInt("stepNum", stepNumber)
                takeExam.arguments = b
                homeItemClickListener?.openCallFragment(takeExam)
                capturePhoto(viewLifecycleOwner.lifecycleScope, this, dispatcherProvider)
            }
        }

        fragmentCourseStepBinding.btnTakeSurvey.setOnClickListener {
            if (stepSurvey.isNotEmpty()) {
                SubmissionsAdapter.openSurvey(homeItemClickListener, stepSurvey[0].id, false, false, "")
            }
        }
        fragmentCourseStepBinding.btnResources.visibility = View.GONE

        fragmentCourseStepBinding.btnAskAi.setOnClickListener {
            openChatFragment()
        }
    }

    private fun openChatFragment(selectedText: String = "") {
        val chatFragment = ChatDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ChatDetailFragment.ARG_COURSE_TITLE, courseTitle)
                putString(ChatDetailFragment.ARG_STEP_TITLE, step.stepTitle)
                putString(ChatDetailFragment.ARG_STEP_DESCRIPTION, step.description)
                putInt(ChatDetailFragment.ARG_STEP_NUMBER, stepNumber)
                putString(ChatDetailFragment.ARG_SELECTED_TEXT, selectedText)
            }
        }
        homeItemClickListener?.openCallFragment(chatFragment)
    }

    private fun createAiSelectionCallback(): ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, MENU_ITEM_ASK_AI, Menu.NONE, getString(R.string.ask_ai))
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId == MENU_ITEM_ASK_AI) {
                val tv = fragmentCourseStepBinding.description
                val start = tv.selectionStart
                val end = tv.selectionEnd
                if (start >= 0 && end > start) {
                    openChatFragment(tv.text.subSequence(start, end).toString())
                    mode.finish()
                    return true
                }
            }
            return false
        }
        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    private companion object {
        const val MENU_ITEM_ASK_AI = 42
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        refreshInlineResources()
    }

    override fun onImageCapture(fileUri: String?) {}

    override fun onDestroyView() {
        super.onDestroyView()
        loadDataJob?.cancel()
        CameraUtils.release()
    }
}
