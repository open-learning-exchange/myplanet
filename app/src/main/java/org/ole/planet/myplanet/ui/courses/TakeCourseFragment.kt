package org.ole.planet.myplanet.ui.courses

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import io.realm.Realm
import kotlinx.coroutines.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTakeCourseBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseActivity.Companion.createActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.getCurrentProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getCourseStepIds
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getCourseSteps
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.isStepCompleted
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.DialogUtils.getAlertDialog
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Locale
import kotlin.collections.isNotEmpty

class TakeCourseFragment : Fragment(), ViewPager.OnPageChangeListener, View.OnClickListener {
    private lateinit var fragmentTakeCourseBinding: FragmentTakeCourseBinding
    lateinit var dbService: DatabaseService
    lateinit var mRealm: Realm
    private var currentCourse: RealmMyCourse? = null
    lateinit var steps: List<RealmCourseStep?>
    var position = 0
    private var currentStep = 0

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
        fragmentTakeCourseBinding = FragmentTakeCourseBinding.inflate(inflater, container, false)
        dbService = DatabaseService(requireActivity())
        mRealm = dbService.realmInstance
        userModel = UserProfileDbHandler(requireContext()).userModel
        currentCourse = mRealm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
        return fragmentTakeCourseBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentTakeCourseBinding.tvCourseTitle.text = currentCourse?.courseTitle
        steps = getCourseSteps(mRealm, courseId)
        if (steps.isEmpty()) {
            fragmentTakeCourseBinding.nextStep.visibility = View.GONE
            fragmentTakeCourseBinding.previousStep.visibility = View.GONE
        }
        fragmentTakeCourseBinding.viewPager2.adapter = CoursesPagerAdapter(this, courseId, getCourseStepIds(mRealm, courseId))
        fragmentTakeCourseBinding.viewPager2.isUserInputEnabled = false

        currentStep = getCourseProgress()

        position = if (currentStep > 0) currentStep - 1 else 0
        fragmentTakeCourseBinding.viewPager2.currentItem = position
        updateStepDisplay(position)

        if (position == 0) {
            fragmentTakeCourseBinding.previousStep.visibility = View.GONE
        }
        setCourseData()
        setListeners()
        fragmentTakeCourseBinding.viewPager2.currentItem = position
        checkSurveyCompletion()
        fragmentTakeCourseBinding.backButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setListeners() {
        fragmentTakeCourseBinding.nextStep.setOnClickListener(this)
        fragmentTakeCourseBinding.previousStep.setOnClickListener(this)
        fragmentTakeCourseBinding.btnRemove.setOnClickListener(this)
        fragmentTakeCourseBinding.finishStep.setOnClickListener(this)
        fragmentTakeCourseBinding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                val currentProgress = getCurrentProgress(steps, mRealm, userModel?.id, courseId)
                if (b && i <= currentProgress + 1) {
                    fragmentTakeCourseBinding.viewPager2.currentItem = i
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateStepDisplay(position: Int) {
        val currentPosition = position + 1
        fragmentTakeCourseBinding.tvStep.text = String.format(getString(R.string.step) + " %d/%d", currentPosition, steps.size)

        val currentProgress = getCurrentProgress(steps, mRealm, userModel?.id, courseId)
        if (currentProgress < steps.size) {
            fragmentTakeCourseBinding.courseProgress.secondaryProgress = currentProgress + 1
        }
        fragmentTakeCourseBinding.courseProgress.progress = currentProgress
    }

    private fun setCourseData() {
        val isGuest = userModel?.isGuest() == true
        val containsUserId = currentCourse?.userId?.contains(userModel?.id) == true
        val stepsSize = steps.size
        val currentItem = fragmentTakeCourseBinding.viewPager2.currentItem

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                if (!isGuest && !containsUserId) {
                    fragmentTakeCourseBinding.btnRemove.visibility = View.VISIBLE
                    fragmentTakeCourseBinding.btnRemove.text = getString(R.string.join)
                    getAlertDialog(requireActivity(), getString(R.string.do_you_want_to_join_this_course), getString(R.string.join_this_course)) { _: DialogInterface?, _: Int ->
                        addRemoveCourse()
                    }
                } else {
                    fragmentTakeCourseBinding.btnRemove.visibility = View.GONE
                }
            }
            
            val detachedUserModel = userModel?.let { mRealm.copyFromRealm(it) }
            val detachedCurrentCourse = currentCourse?.let { mRealm.copyFromRealm(it) }

            withContext(Dispatchers.IO) {
                try {
                    Realm.getDefaultInstance().use { backgroundRealm ->
                        createActivity(backgroundRealm, detachedUserModel, detachedCurrentCourse)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                fragmentTakeCourseBinding.courseProgress.max = stepsSize
                updateStepDisplay(currentItem)

                if (containsUserId) {
                    fragmentTakeCourseBinding.nextStep.visibility = View.VISIBLE
                    fragmentTakeCourseBinding.courseProgress.visibility = View.VISIBLE
                } else {
                    fragmentTakeCourseBinding.nextStep.visibility = View.GONE
                    fragmentTakeCourseBinding.previousStep.visibility = View.GONE
                    fragmentTakeCourseBinding.courseProgress.visibility = View.GONE
                }
            }
        }
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        if (position > 0) {
            if (position - 1 < steps.size) changeNextButtonState(position)
        } else {
            fragmentTakeCourseBinding.nextStep.visibility = View.VISIBLE
            fragmentTakeCourseBinding.nextStep.isClickable = true
            fragmentTakeCourseBinding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
        }

        updateStepDisplay(position)
    }

    private fun changeNextButtonState(position: Int) {
        if (isStepCompleted(mRealm, steps[position - 1]?.id, userModel?.id)) {
            fragmentTakeCourseBinding.nextStep.isClickable = true
            fragmentTakeCourseBinding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
        } else {
            fragmentTakeCourseBinding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
            fragmentTakeCourseBinding.nextStep.isClickable = false
        }
    }

    override fun onPageScrollStateChanged(state: Int) {}

    private fun onClickNext() {
        fragmentTakeCourseBinding.tvStep.text = String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", fragmentTakeCourseBinding.viewPager2.currentItem, currentCourse?.courseSteps?.size)
        if (fragmentTakeCourseBinding.viewPager2.currentItem == currentCourse?.courseSteps?.size) {
            fragmentTakeCourseBinding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
            fragmentTakeCourseBinding.nextStep.visibility = View.GONE
            fragmentTakeCourseBinding.finishStep.visibility = View.VISIBLE
        }else{
            fragmentTakeCourseBinding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
            fragmentTakeCourseBinding.nextStep.visibility = View.VISIBLE
            fragmentTakeCourseBinding.finishStep.visibility = View.GONE

        }
    }

    private fun onClickPrevious() {
        fragmentTakeCourseBinding.tvStep.text = String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", fragmentTakeCourseBinding.viewPager2.currentItem - 1, currentCourse?.courseSteps?.size)
        if (fragmentTakeCourseBinding.viewPager2.currentItem - 1 == 0) {
            fragmentTakeCourseBinding.previousStep.visibility = View.GONE
            fragmentTakeCourseBinding.nextStep.visibility = View.VISIBLE
            fragmentTakeCourseBinding.finishStep.visibility = View.GONE
        }else{
            fragmentTakeCourseBinding.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
            fragmentTakeCourseBinding.nextStep.visibility = View.VISIBLE
            fragmentTakeCourseBinding.finishStep.visibility = View.GONE
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.next_step -> {
                if (isValidClickRight) {
                    fragmentTakeCourseBinding.viewPager2.currentItem += 1
                    fragmentTakeCourseBinding.previousStep.visibility = View.VISIBLE
                }
                onClickNext()
            }

            R.id.previous_step -> {
                onClickPrevious()
                if (isValidClickLeft) {
                    fragmentTakeCourseBinding.viewPager2.currentItem -= 1
                }
            }

            R.id.finish_step -> checkSurveyCompletion()
            R.id.btn_remove -> addRemoveCourse()
        }
    }

    private fun addRemoveCourse() {
        Realm.getDefaultInstance().executeTransactionAsync({ backgroundRealm ->
            val detachedCourse = backgroundRealm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()

            if (detachedCourse != null) {
                if (detachedCourse.userId?.contains(userModel?.id) == true) {
                    detachedCourse.removeUserId(userModel?.id)
                    onRemove(backgroundRealm, "courses", userModel?.id, courseId)
                } else {
                    detachedCourse.setUserId(userModel?.id)
                    onAdd(backgroundRealm, "courses", userModel?.id, courseId)
                }
            }
        }, {
            setCourseData()
        }, { error ->
            error.printStackTrace()
        })
    }

    private fun getCourseProgress(): Int {
        val realm = DatabaseService(requireActivity()).realmInstance
        val user = UserProfileDbHandler(requireActivity()).userModel
        val courseProgressMap = RealmCourseProgress.getCourseProgress(realm, user?.id)
        val courseProgress = courseProgressMap[courseId]?.asJsonObject?.get("current")?.asInt
        return courseProgress ?: 0
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
            fragmentTakeCourseBinding.finishStep.setOnClickListener {
                Toast.makeText(context, getString(R.string.please_complete_survey), Toast.LENGTH_SHORT).show() }
        } else {
            fragmentTakeCourseBinding.finishStep.isEnabled = true
            fragmentTakeCourseBinding.finishStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
            fragmentTakeCourseBinding.finishStep.setOnClickListener {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    private val isValidClickRight: Boolean get() = fragmentTakeCourseBinding.viewPager2.adapter != null && fragmentTakeCourseBinding.viewPager2.currentItem < fragmentTakeCourseBinding.viewPager2.adapter?.itemCount!!
    private val isValidClickLeft: Boolean get() = fragmentTakeCourseBinding.viewPager2.adapter != null && fragmentTakeCourseBinding.viewPager2.currentItem > 0

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
