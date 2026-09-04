package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.text.Spannable
import android.text.style.URLSpan
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentCourseStepBinding
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.components.CustomClickableSpan
import org.ole.planet.myplanet.ui.exam.ExamTakingFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.utils.CameraUtils
import org.ole.planet.myplanet.utils.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utils.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText
import org.ole.planet.myplanet.utils.ResourcesPreviewLoader

@AndroidEntryPoint
class CourseStepFragment : BaseContainerFragment(), ImageCaptureCallback {

    private val viewModel: CourseStepViewModel by viewModels()

    private lateinit var fragmentCourseStepBinding: FragmentCourseStepBinding
    var stepId: String? = null
    private var nextStepId: String? = null
    private lateinit var step: CourseStep
    private var resources: List<MyLibrary> = emptyList()
    private var stepExams: List<StepExam> = emptyList()
    private var stepSurvey: List<StepExam> = emptyList()
    var user: UserEntity? = null
    private var stepNumber = 0
    private var courseTitle: String? = null
    private var inlineResourceAdapter: InlineResourceAdapter? = null
    private var userHasCourse = false

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val loadedStep = state.step ?: return@collect
                    step = loadedStep
                    resources = state.resources
                    stepExams = state.stepExams
                    stepSurvey = state.stepSurvey
                    courseTitle = state.courseTitle
                    userHasCourse = state.userHasCourse
                    user = state.user

                    fragmentCourseStepBinding.btnResources.text =
                        getString(R.string.resources_size, state.resources.size)
                    hideTestIfNoQuestion(state.hasExam, state.hasSurvey, state.stepExams)
                    fragmentCourseStepBinding.tvTitle.text = step.stepTitle

                    fragmentCourseStepBinding.description.setTextIsSelectable(true)
                    fragmentCourseStepBinding.description.customSelectionActionModeCallback = createAiSelectionCallback()
                    setMarkdownText(
                        fragmentCourseStepBinding.description,
                        state.markdownDescription
                    )

                    if (!userHasCourse) {
                        fragmentCourseStepBinding.btnTakeTest.visibility = View.GONE
                        fragmentCourseStepBinding.btnTakeSurvey.visibility = View.GONE
                    }

                    fragmentCourseStepBinding.btnAskAi.visibility = View.VISIBLE
                    setupInlineResources()

                    if (state.isDownloadingResources) {
                        fragmentCourseStepBinding.resourceDownloadProgress.visibility = View.VISIBLE
                    } else {
                        fragmentCourseStepBinding.resourceDownloadProgress.visibility = View.GONE
                    }

                    val textWithSpans = fragmentCourseStepBinding.description.text
                    if (textWithSpans is Spannable) {
                        val urlSpans = textWithSpans.getSpans(0, textWithSpans.length, URLSpan::class.java)
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

                    if (userHasCourse) {
                        viewModel.saveCourseProgress(stepNumber)
                    }
                }
            }
        }

        viewModel.loadStep(stepId, stepNumber, nextStepId)
    }

    private fun setupInlineResources() {
        if (resources.isEmpty()) {
            fragmentCourseStepBinding.tvResourcesHeader.visibility = View.GONE
            fragmentCourseStepBinding.rvInlineResources.visibility = View.GONE
            return
        }

        fragmentCourseStepBinding.tvResourcesHeader.visibility = View.VISIBLE
        fragmentCourseStepBinding.rvInlineResources.visibility = View.VISIBLE

        if (inlineResourceAdapter == null) {
            inlineResourceAdapter = InlineResourceAdapter(
                ResourcesPreviewLoader(dispatcherProvider),
                dispatcherProvider
            ) { library ->
                openResource(library)
            }
            fragmentCourseStepBinding.rvInlineResources.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = inlineResourceAdapter
            }
        }
        inlineResourceAdapter?.submitList(resources)
    }

    private fun hideTestIfNoQuestion(isTestPresent: Boolean, isSurveyPresent: Boolean, exams: List<StepExam>) {
        fragmentCourseStepBinding.btnTakeTest.visibility = View.GONE
        fragmentCourseStepBinding.btnTakeSurvey.visibility = View.GONE
        if (exams.isNotEmpty()) {
            fragmentCourseStepBinding.btnTakeTest.text = if (isTestPresent) {
                getString(R.string.retake_test, exams.size)
            } else {
                getString(R.string.take_test, exams.size)
            }
            fragmentCourseStepBinding.btnTakeTest.visibility = View.VISIBLE
        }
        if (stepSurvey.isNotEmpty()) {
            fragmentCourseStepBinding.btnTakeSurvey.text = if (isSurveyPresent) {
                getString(R.string.redo_survey)
            } else {
                getString(R.string.record_survey)
            }
            fragmentCourseStepBinding.btnTakeSurvey.visibility = View.VISIBLE
        }
    }

    override fun setMenuVisibility(visible: Boolean) {
        super.setMenuVisibility(visible)
        if (!isAdded || !::step.isInitialized) return
        try {
            if (visible && userHasCourse) {
                viewModel.saveCourseProgress(stepNumber)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                if (start in 0..<end) {
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
        viewModel.refreshInlineResources(stepId)
    }

    override fun onImageCapture(fileUri: String?) {}

    override fun onDestroyView() {
        super.onDestroyView()
        CameraUtils.release()
        fragmentCourseStepBinding.rvInlineResources.adapter = null
        inlineResourceAdapter = null
    }
}
