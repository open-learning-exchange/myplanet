package org.ole.planet.myplanet.ui.courses

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTakeCourseBinding
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.ratings.RatingsFragment
import org.ole.planet.myplanet.utils.DialogUtils.getDialog
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class TakeCourseFragment : Fragment(), ViewPager.OnPageChangeListener, View.OnClickListener {
    private var isNextStepLocked = false
    private var lockedStepMessage = ""
    private var _binding: FragmentTakeCourseBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var userSessionManager: UserSessionManager
    private val viewModel: TakeCourseViewModel by viewModels()
    private var courseId: String? = null
    private var userModel: UserEntity? = null
    private var currentCourse: MyCourse? = null
    lateinit var steps: List<CourseStep?>
    var position = 0
    private var currentStep = 0
    private var currentCourseProgress = 0
    private var joinDialog: AlertDialog? = null
    private var lastPositionBeforeExam = -1
    private var pendingJoinDialog = false
    private var courseDetailContentReady = false
    private var coursesPagerAdapter: CoursesPagerAdapter? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            courseId = requireArguments().getString("id")
            if (requireArguments().containsKey("position")) {
                position = requireArguments().getInt("position")
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTakeCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListeners()
        binding.backButton.setOnClickListener {
            FragmentNavigator.popBackStack(requireActivity().supportFragmentManager)
        }

        collectLatestWhenStarted(viewModel.uiState) { state ->
            when (state) {
                is TakeCourseUiState.Loading -> {
                    binding.loadingIndicator.visibility = View.VISIBLE
                    binding.contentLayout.visibility = View.GONE
                }
                is TakeCourseUiState.NotFound -> {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(requireContext(), getString(R.string.failed_to_load_course), Toast.LENGTH_LONG).show()
                    requireActivity().supportFragmentManager.popBackStack()
                }
                is TakeCourseUiState.Success -> bindCourse(state)
            }
        }

        courseId?.let { viewModel.loadCourse(it) }
    }

    private fun bindCourse(state: TakeCourseUiState.Success) {
        if (_binding == null) return

        userModel = state.userModel
        currentCourse = state.course
        steps = state.steps
        currentStep = state.courseProgress
        currentCourseProgress = currentStep

        binding.loadingIndicator.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
        binding.tvCourseTitle.text = currentCourse?.courseTitle

        if (steps.isEmpty()) {
            binding.nextStep.visibility = View.GONE
            binding.previousStep.visibility = View.GONE
        }

        position = if (lastPositionBeforeExam > 0) lastPositionBeforeExam else if (currentStep > 0) currentStep else 0
        lastPositionBeforeExam = -1
        setNavigationButtons()

        if (coursesPagerAdapter == null) {
            coursesPagerAdapter = CoursesPagerAdapter(
                this@TakeCourseFragment,
                courseId
            )
            binding.viewPager2.adapter = coursesPagerAdapter

            pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    this@TakeCourseFragment.onPageSelected(position)
                }
            }
            pageChangeCallback?.let { binding.viewPager2.registerOnPageChangeCallback(it) }
        }
        coursesPagerAdapter?.submitList(steps.mapNotNull { it?.id })

        binding.viewPager2.isUserInputEnabled = false
        binding.viewPager2.setCurrentItem(position, false)
        updateStepDisplay(position)
        if (position == 0) {
            binding.previousStep.visibility = View.GONE
        }
        setCourseData()
    }

    override fun onResume() {
        super.onResume()
        if (this::steps.isInitialized) {
            val currentPosition = if (lastPositionBeforeExam > 0) lastPositionBeforeExam else binding.viewPager2.currentItem
            updateStepDisplay(currentPosition)
            if (lastPositionBeforeExam > 0) {
                binding.viewPager2.setCurrentItem(lastPositionBeforeExam, false)
            }
            if (currentPosition >= steps.size) {
                binding.nextStep.visibility = View.GONE
                binding.finishStep.visibility = View.VISIBLE
            } else {
                binding.nextStep.visibility = View.VISIBLE
                binding.finishStep.visibility = View.GONE
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::steps.isInitialized && _binding != null) {
            lastPositionBeforeExam = binding.viewPager2.currentItem
        }
    }

    private fun setListeners() {
        binding.nextStep.setOnClickListener(this)
        binding.previousStep.setOnClickListener(this)
        binding.btnRemove.setOnClickListener(this)
        binding.finishStep.setOnClickListener(this)
        binding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (b && i <= currentCourseProgress + 1) {
                    binding.viewPager2.currentItem = i
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setStepText(currentStep: Int, totalSteps: Int) {
        binding.tvStep.text = String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", currentStep, totalSteps)
    }

    private fun updateStepDisplay(position: Int) {
        if (position == 0) {
            binding.tvStep.text = "Course Details"
        } else {
            setStepText(position, steps.size)
        }
        binding.nextStep.text = if (position == 0) getString(R.string.start) else getString(R.string.next)
        binding.courseStepProgressBar.max = steps.size
        binding.courseStepProgressBar.progress = position
        viewLifecycleOwner.lifecycleScope.launch {
            val currentProgress = viewModel.getCurrentProgress(steps, userModel?.id, courseId)
            currentCourseProgress = currentProgress
            if (currentProgress < steps.size) {
                binding.courseProgress.secondaryProgress = currentProgress + 1
            }
            binding.courseProgress.progress = currentProgress
        }
    }

    private fun setCourseData() {
        val isGuest = userModel?.isGuest() == true
        val containsUserId = currentCourse?.userId?.contains(userModel?.id) == true
        val stepsSize = steps.size

        if (!isGuest && !containsUserId) {
            binding.btnRemove.visibility = View.VISIBLE
            binding.btnRemove.text = getString(R.string.join)
            if (!viewModel.hasOfferedJoinDialog) {
                viewModel.markJoinDialogOffered()
                joinDialog = getDialog(
                    requireActivity(),
                    getString(R.string.do_you_want_to_join_this_course),
                    getString(R.string.join_this_course)
                ) { _: DialogInterface?, _: Int ->
                    addRemoveCourse()
                }
                joinDialog?.show()
            }
        } else {
            binding.btnRemove.visibility = View.GONE
        }

        binding.courseProgress.max = stepsSize
        binding.courseProgress.visibility = if (containsUserId) View.VISIBLE else View.GONE

        updateNavigationVisibility()

        val detachedUserModel = userModel
        val detachedCurrentCourse = currentCourse
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                detachedCurrentCourse?.courseId?.let { cId ->
                    detachedCurrentCourse.courseTitle?.let { courseTitle ->
                        detachedUserModel?.name?.let { userName ->
                            viewModel.logCourseVisit(cId, courseTitle, userName)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateNavigationVisibility() {
        val containsUserId = currentCourse?.userId?.contains(userModel?.id) == true
        if (containsUserId) {
            if (position >= steps.size) {
                binding.nextStep.visibility = View.GONE
                binding.finishStep.visibility = View.VISIBLE
            } else {
                binding.nextStep.visibility = View.VISIBLE
                binding.finishStep.visibility = View.GONE
            }
            binding.previousStep.visibility = if (position == 0) View.GONE else View.VISIBLE
        } else {
            binding.nextStep.visibility = View.GONE
            binding.previousStep.visibility = View.GONE
        }

        binding.root.post {
            if (_binding != null) {
                binding.contentLayout.requestLayout()
                binding.nextStep.requestLayout()
                binding.previousStep.requestLayout()
                binding.finishStep.requestLayout()
            }
        }
    }

    fun onCourseDetailContentReady() {
        courseDetailContentReady = true
        maybeShowJoinDialog()
    }

    fun navigateToStep(stepId: String) {
        if (_binding == null || !this::steps.isInitialized) return
        val containsUserId = currentCourse?.userId?.contains(userModel?.id) == true
        if (!containsUserId) return
        val index = steps.indexOfFirst { it?.id == stepId }
        if (index < 0) return
        binding.viewPager2.setCurrentItem(index + 1, true)
    }

    private fun maybeShowJoinDialog() {
        if (!pendingJoinDialog || !courseDetailContentReady || _binding == null || !isAdded) return
        pendingJoinDialog = false
        joinDialog?.show()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        if (!this::steps.isInitialized) return
        isNextStepLocked = false
        this.position = position
        if (position > 0 && position - 1 < steps.size) {
            changeNextButtonState(position)
        }
        updateNavigationVisibility()
        updateStepDisplay(position)
    }

    private fun changeNextButtonState(position: Int) {
        if (courseId == "4e6b78800b6ad18b4e8b0e1e38a98cac") {
            val stepId = steps.getOrNull(position - 1)?.id
            viewLifecycleOwner.lifecycleScope.launch {
                val stepData = stepId?.let { viewModel.getCourseStepData(it, userModel?.id) }
                val hasExam = stepData?.stepExams?.isNotEmpty() == true
                val hasSurvey = stepData?.stepSurvey?.isNotEmpty() == true

                if (viewModel.isStepCompleted(stepId, userModel?.id)) {
                    isNextStepLocked = false
                } else if (hasExam || hasSurvey) {
                    isNextStepLocked = true
                    lockedStepMessage = when {
                        hasExam -> getString(R.string.please_complete_test)
                        else -> getString(R.string.please_complete_survey)
                    }
                } else {
                    isNextStepLocked = false
                }
            }
        } else {
            isNextStepLocked = false
        }
    }
    override fun onPageScrollStateChanged(state: Int) {}

    private fun onClickNext() {
        setStepText(binding.viewPager2.currentItem, steps.size)
        if (binding.viewPager2.currentItem >= steps.size) {
            binding.nextStep.visibility = View.GONE
            binding.finishStep.visibility = View.VISIBLE
        } else {
            binding.nextStep.visibility = View.VISIBLE
            binding.finishStep.visibility = View.GONE
        }
    }

    private fun onClickPrevious() {
        setStepText(binding.viewPager2.currentItem - 1, steps.size)
        if (binding.viewPager2.currentItem - 1 == 0) {
            binding.previousStep.visibility = View.GONE
            binding.nextStep.visibility = View.VISIBLE
            binding.finishStep.visibility = View.GONE
        }else{
            binding.nextStep.visibility = View.VISIBLE
            binding.finishStep.visibility = View.GONE
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.next_step -> {
                if (isNextStepLocked) {
                    Snackbar.make(binding.root, lockedStepMessage, Snackbar.LENGTH_SHORT).show()
                    return
                }
                if (isValidClickRight) {
                    binding.viewPager2.currentItem += 1
                    binding.previousStep.visibility = View.VISIBLE
                }
                onClickNext()
            }

            R.id.previous_step -> {
                onClickPrevious()
                if (isValidClickLeft) {
                    binding.viewPager2.currentItem -= 1
                }
            }

            R.id.finish_step -> onFinishStep()
            R.id.btn_remove -> addRemoveCourse()
        }
    }

    private suspend fun showCourseRatingDialogAndFinish() {
        val cId = courseId ?: currentCourse?.courseId
        val title = currentCourse?.courseTitle ?: ""
        val userId = userModel?.id
        val decision = viewModel.getRatingPromptDecision(cId, userId)

        if (cId != null && decision == RatingPromptDecision.Show && isAdded) {
            val ratingDialog = RatingsFragment.newInstance("course", cId, title)
            ratingDialog.setOnDismissListener {
                if (isAdded) {
                    FragmentNavigator.popBackStack(requireActivity().supportFragmentManager)
                }
            }
            ratingDialog.show(parentFragmentManager, RatingsFragment.TAG)
        } else if (isAdded) {
            FragmentNavigator.popBackStack(requireActivity().supportFragmentManager)
        }
    }

    private fun onFinishStep() {
        viewLifecycleOwner.lifecycleScope.launch {
            val hasUnfinishedSurvey = courseId?.let {
                viewModel.hasUnfinishedSurveys(it, userModel?.id)
            } ?: false

            if (hasUnfinishedSurvey && courseId == MANDATORY_SURVEY_COURSE_ID) {
                Toast.makeText(context, getString(R.string.please_complete_survey), Toast.LENGTH_SHORT).show()
            } else {
                showCourseRatingDialogAndFinish()
            }
        }
    }

    private fun addRemoveCourse() {
        viewLifecycleOwner.lifecycleScope.launch {
            val course = courseId?.let { viewModel.getCourseById(it) }
            val isJoined = course?.userId?.contains(userModel?.id) == true

            val userId = userModel?.id ?: return@launch
            val cId = courseId ?: return@launch

            val result = if (isJoined) {
                viewModel.leaveCourse(cId, userId)
            } else {
                viewModel.joinCourse(cId, userId)
            }

            result.onSuccess {
                val updatedUserIds = if (isJoined) {
                    currentCourse?.userId.orEmpty().filter { it != userId }
                } else {
                    (currentCourse?.userId.orEmpty() + userId).distinct()
                }
                currentCourse = currentCourse?.copy(userId = updatedUserIds)
                if (_binding != null) {
                    setCourseData()
                }

                viewModel.loadCourse(cId, forceRefresh = true)

                val statusMessage = if (isJoined) {
                    getString(R.string.removed_from)
                } else {
                    getString(R.string.added_to)
                }

                Utilities.toast(activity, "course $statusMessage ${getString(R.string.my_courses)}")
            }.onFailure { e ->
                e.printStackTrace()
                Utilities.toast(activity, "Failed to update course: ${e.message}")
            }
        }
    }

    private fun setNavigationButtons(){
        if(position >= steps.size){
            binding.nextStep.visibility = View.GONE
            binding.finishStep.visibility = View.VISIBLE
        } else {
            binding.nextStep.visibility = View.VISIBLE
            binding.finishStep.visibility = View.GONE
        }

    }

    override fun onDestroyView() {
        binding.courseProgress.setOnSeekBarChangeListener(null)
        pageChangeCallback?.let { binding.viewPager2.unregisterOnPageChangeCallback(it) }
        pageChangeCallback = null
        lifecycleScope.coroutineContext.cancelChildren()
        joinDialog?.dismiss()
        joinDialog = null
        _binding = null
        coursesPagerAdapter = null
        super.onDestroyView()
    }

    private val isValidClickRight: Boolean get() = binding.viewPager2.adapter != null && binding.viewPager2.currentItem < (binding.viewPager2.adapter?.itemCount ?: 0)
    private val isValidClickLeft: Boolean get() = binding.viewPager2.adapter != null && binding.viewPager2.currentItem > 0

    companion object {
        // Special course with mandatory completion survey (e.g. MyPlanet Onboarding course)
        private const val MANDATORY_SURVEY_COURSE_ID = "4e6b78800b6ad18b4e8b0e1e38a98cac"
        private const val JOIN_DIALOG_FALLBACK_MS = 5000L

        fun newInstance(b: Bundle?): TakeCourseFragment {
            val takeCourseFragment = TakeCourseFragment()
            takeCourseFragment.arguments = b
            return takeCourseFragment
        }
    }
}
