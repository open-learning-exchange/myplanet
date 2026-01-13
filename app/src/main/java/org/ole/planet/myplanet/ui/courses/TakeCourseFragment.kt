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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.isNotEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.databinding.FragmentTakeCourseBinding
import org.ole.planet.myplanet.model.RealmCourseActivity.Companion.createActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.getCurrentProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.isStepCompleted
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.service.UserSessionManager
import org.ole.planet.myplanet.utilities.DialogUtils.getDialog
import org.ole.planet.myplanet.utilities.NavigationHelper
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TakeCourseFragment : Fragment(), ViewPager.OnPageChangeListener, View.OnClickListener {
    private var _binding: FragmentTakeCourseBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var userSessionManager: UserSessionManager
    @Inject
    lateinit var coursesRepository: CoursesRepository
    lateinit var mRealm: Realm
    private var currentCourse: RealmMyCourse? = null
    lateinit var steps: List<RealmCourseStep?>
    var position = 0
    private var currentStep = 0
    private var cachedCourseProgress: Int? = null
    private val isFetchingProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    private var joinDialog: AlertDialog? = null

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
        mRealm = databaseService.realmInstance
        userModel = userSessionManager.userModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val course: RealmMyCourse? = courseId?.let { coursesRepository.getCourseById(it) }
            binding.loadingIndicator.visibility = View.GONE
            if (course == null) {
                Toast.makeText(requireContext(), getString(R.string.failed_to_load_course), Toast.LENGTH_LONG).show()
                requireActivity().supportFragmentManager.popBackStack()
                return@launch
            }
            binding.contentLayout.visibility = View.VISIBLE
            currentCourse = course
            binding.tvCourseTitle.text = currentCourse?.courseTitle

            withContext(Dispatchers.IO) {
                steps = coursesRepository.getCourseSteps(courseId)

                if (cachedCourseProgress == null && isFetchingProgress.compareAndSet(false, true)) {
                    try {
                        cachedCourseProgress = getCourseProgress()
                    } finally {
                        isFetchingProgress.set(false)
                    }
                }
            }

            currentStep = cachedCourseProgress ?: 0

            if (steps.isEmpty()) {
                binding.nextStep.visibility = View.GONE
                binding.previousStep.visibility = View.GONE
            }

            position = if (currentStep > 0) currentStep else 0
            setNavigationButtons()
            binding.viewPager2.adapter =
                CoursesPagerAdapter(
                    this@TakeCourseFragment,
                    courseId,
                    steps.mapNotNull { it?.id }.toTypedArray()
                )
            binding.viewPager2.isUserInputEnabled = false
            binding.viewPager2.setCurrentItem(position, false)
            binding.viewPager2.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    this@TakeCourseFragment.onPageSelected(position)
                }
            })
            updateStepDisplay(position)
            if (position == 0) {
                binding.previousStep.visibility = View.GONE
            }
            setCourseData()
            setListeners()
            checkSurveyCompletion()
            binding.backButton.setOnClickListener {
                NavigationHelper.popBackStack(requireActivity().supportFragmentManager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::steps.isInitialized) {
            val currentPosition = binding.viewPager2.currentItem
            updateStepDisplay(currentPosition)

            // Update Next/Finish button visibility when returning from exam
            if (currentPosition >= steps.size) {
                binding.nextStep.visibility = View.GONE
                binding.finishStep.visibility = View.VISIBLE
            } else {
                binding.nextStep.visibility = View.VISIBLE
                binding.finishStep.visibility = View.GONE
            }
        }
    }

    private fun setListeners() {
        binding.nextStep.setOnClickListener(this)
        binding.previousStep.setOnClickListener(this)
        binding.btnRemove.setOnClickListener(this)
        binding.finishStep.setOnClickListener(this)
        binding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                val currentProgress = getCurrentProgress(steps, mRealm, userModel?.id, courseId)
                if (b && i <= currentProgress + 1) {
                    binding.viewPager2.currentItem = i
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateStepDisplay(position: Int) {
        if (position == 0) {
            binding.tvStep.text = "Course Details"
        } else {
            val stepNumber = position
            binding.tvStep.text = String.format(getString(R.string.step) + " %d/%d", stepNumber, steps.size)
        }

        val currentProgress = getCurrentProgress(steps, mRealm, userModel?.id, courseId)
        if (currentProgress < steps.size) {
            binding.courseProgress.secondaryProgress = currentProgress + 1
        }
        binding.courseProgress.progress = currentProgress
    }

    private fun setCourseData() {
        val isGuest = userModel?.isGuest() == true
        val containsUserId = currentCourse?.userId?.contains(userModel?.id) == true
        val stepsSize = steps.size

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                if (!isGuest && !containsUserId) {
                    binding.btnRemove.visibility = View.VISIBLE
                    binding.btnRemove.text = getString(R.string.join)
                    joinDialog = getDialog(
                        requireActivity(),
                        getString(R.string.do_you_want_to_join_this_course),
                        getString(R.string.join_this_course)
                    ) { _: DialogInterface?, _: Int ->
                        addRemoveCourse()
                    }
                    joinDialog?.show()
                } else {
                    binding.btnRemove.visibility = View.GONE
                }
            }

            val detachedUserModel = userModel
            val detachedCurrentCourse = currentCourse

            withContext(Dispatchers.IO) {
                val backgroundRealm = databaseService.realmInstance
                try {
                    createActivity(backgroundRealm, detachedUserModel, detachedCurrentCourse)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    backgroundRealm.close()
                }
            }

            withContext(Dispatchers.Main) {
                binding.courseProgress.max = stepsSize

                if (containsUserId) {
                    if(position < steps.size - 1){
                        binding.nextStep.visibility = View.VISIBLE
                    }
                    binding.courseProgress.visibility = View.VISIBLE
                } else {
                    binding.nextStep.visibility = View.GONE
                    binding.previousStep.visibility = View.GONE
                    binding.courseProgress.visibility = View.GONE
                }
            }
        }
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        if (position > 0) {
            if (position - 1 < steps.size) changeNextButtonState(position)
        } else {
            binding.nextStep.visibility = View.VISIBLE
            binding.nextStep.isClickable = true
            binding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
        }

        updateStepDisplay(position)
    }

    private fun changeNextButtonState(position: Int) {
        if (courseId == "4e6b78800b6ad18b4e8b0e1e38a98cac") {
            if (isStepCompleted(mRealm, steps[position - 1]?.id, userModel?.id)) {
                binding.nextStep.isClickable = true
                binding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
            } else {
                binding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
                binding.nextStep.isClickable = false
            }
        } else {
            binding.nextStep.isClickable = true
            binding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
        }
    }

    override fun onPageScrollStateChanged(state: Int) {}

    private fun onClickNext() {
        binding.tvStep.text = String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", binding.viewPager2.currentItem, steps.size)
        if (binding.viewPager2.currentItem >= steps.size) {
            binding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
            binding.nextStep.visibility = View.GONE
            binding.finishStep.visibility = View.VISIBLE
        } else {
            binding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
            binding.nextStep.visibility = View.VISIBLE
            binding.finishStep.visibility = View.GONE
        }
    }

    private fun onClickPrevious() {
        binding.tvStep.text = String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", binding.viewPager2.currentItem - 1, steps.size)
        if (binding.viewPager2.currentItem - 1 == 0) {
            binding.previousStep.visibility = View.GONE
            binding.nextStep.visibility = View.VISIBLE
            binding.finishStep.visibility = View.GONE
        }else{
            binding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
            binding.nextStep.visibility = View.VISIBLE
            binding.finishStep.visibility = View.GONE
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.next_step -> {
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

            R.id.finish_step -> checkSurveyCompletion()
            R.id.btn_remove -> addRemoveCourse()
        }
    }

    private fun addRemoveCourse() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val isJoined = withContext(Dispatchers.IO) {
                    val course = courseId?.let { coursesRepository.getCourseById(it) }
                    course?.userId?.contains(userModel?.id) == true
                }

                userModel?.id?.let { userId ->
                    courseId?.let { cId ->
                        if (isJoined) {
                            coursesRepository.leaveCourse(cId, userId)
                        } else {
                            coursesRepository.joinCourse(cId, userId)
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    val updatedCourse = courseId?.let { coursesRepository.getCourseById(it) }
                    if (updatedCourse != null) {
                        currentCourse = updatedCourse
                    }
                }

                val statusMessage = if (isJoined) {
                    getString(R.string.removed_from)
                } else {
                    getString(R.string.added_to)
                }

                Utilities.toast(activity, "course $statusMessage ${getString(R.string.my_courses)}")
                setCourseData()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    e.printStackTrace()
                    Utilities.toast(activity, "Failed to update course: ${e.message}")
                }
            }
        }
    }

    private suspend fun getCourseProgress(): Int {
        return withContext(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                val user = userSessionManager.userModel
                val courseProgressMap = RealmCourseProgress.getCourseProgress(realm, user?.id)
                courseProgressMap[courseId]?.asJsonObject?.get("current")?.asInt ?: 0
            }
        }
    }

    private fun checkSurveyCompletion() {
        val hasUnfinishedSurvey = steps.any { step ->
            val stepSurvey = mRealm.where(RealmStepExam::class.java)
                .equalTo("stepId", step?.id)
                .equalTo("type", "surveys")
                .findAll()
            stepSurvey.any { survey -> !existsSubmission(mRealm, survey.id, "survey") }
        }

        if (hasUnfinishedSurvey && courseId == "4e6b78800b6ad18b4e8b0e1e38a98cac") {
            binding.finishStep.setOnClickListener {
                Toast.makeText(context, getString(R.string.please_complete_survey), Toast.LENGTH_SHORT).show() }
        } else {
            binding.finishStep.isEnabled = true
            binding.finishStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
            binding.finishStep.setOnClickListener {
                NavigationHelper.popBackStack(requireActivity().supportFragmentManager)
            }
        }
    }

    private fun setNavigationButtons(){
        if(position >= steps.size - 1){
            binding.nextStep.visibility = View.GONE
            binding.finishStep.visibility = View.VISIBLE
        } else {
            binding.nextStep.visibility = View.VISIBLE
            binding.finishStep.visibility = View.GONE
        }

    }

    override fun onDestroyView() {
        binding.courseProgress.setOnSeekBarChangeListener(null)
        lifecycleScope.coroutineContext.cancelChildren()
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
        joinDialog?.dismiss()
        joinDialog = null
        _binding = null
        super.onDestroyView()
    }

    private val isValidClickRight: Boolean get() = binding.viewPager2.adapter != null && binding.viewPager2.currentItem < binding.viewPager2.adapter?.itemCount!!
    private val isValidClickLeft: Boolean get() = binding.viewPager2.adapter != null && binding.viewPager2.currentItem > 0

    companion object {
        var courseId: String? = null
        var userModel: RealmUserModel? = null

        @JvmStatic
        fun newInstance(b: Bundle?): TakeCourseFragment {
            val takeCourseFragment = TakeCourseFragment()
            takeCourseFragment.arguments = b
            return takeCourseFragment
        }

        fun existsSubmission(mRealm: Realm, firstStepId: String?, submissionType: String): Boolean {
            val questions = mRealm.where(RealmExamQuestion::class.java)
                .equalTo("examId", firstStepId)
                .findAll()

            var isPresent = false
            if (questions != null && questions.isNotEmpty()) {
                val examId = questions[0]?.examId
                val isSubmitted = courseId?.let { courseId ->
                    val parentId = "$examId@$courseId"
                    mRealm.where(RealmSubmission::class.java)
                        .equalTo("userId", userModel?.id)
                        .equalTo("parentId", parentId)
                        .equalTo("type", submissionType)
                        .findFirst() != null
                } == true
                isPresent = isSubmitted
            }
            return isPresent
        }
    }
}
