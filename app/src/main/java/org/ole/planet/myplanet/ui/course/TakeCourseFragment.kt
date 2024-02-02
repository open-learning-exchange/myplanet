package org.ole.planet.myplanet.ui.course

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTakeCourseBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseActivity.Companion.createActivity
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.getCurrentProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmCourseStep.Companion.getStepIds
import org.ole.planet.myplanet.model.RealmCourseStep.Companion.getSteps
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove
import org.ole.planet.myplanet.model.RealmSubmission.Companion.isStepCompleted
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.DialogUtils.getAlertDialog
import org.ole.planet.myplanet.utilities.Utilities

class TakeCourseFragment : Fragment(), ViewPager.OnPageChangeListener, View.OnClickListener {
    private var fragmentTakeCourseBinding: FragmentTakeCourseBinding? = null
    var dbService: DatabaseService? = null
    var mRealm: Realm? = null
    var courseId: String? = null
    var currentCourse: RealmMyCourse? = null
    var steps: List<RealmCourseStep?>? = null
    var userModel: RealmUserModel? = null
    var position = 0
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
        mRealm = dbService!!.realmInstance
        userModel = UserProfileDbHandler(activity).userModel
        currentCourse = mRealm!!.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
        return fragmentTakeCourseBinding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentTakeCourseBinding!!.tvCourseTitle.text = currentCourse!!.courseTitle
        steps = getSteps(mRealm!!, courseId)
        if (steps == null || steps!!.isEmpty()) {
            fragmentTakeCourseBinding!!.nextStep.visibility = View.GONE
            fragmentTakeCourseBinding!!.previousStep.visibility = View.GONE
        }
        fragmentTakeCourseBinding!!.viewPagerCourse.adapter = CoursePagerAdapter(
            childFragmentManager, courseId!!, getStepIds(mRealm!!, courseId)
        )
        fragmentTakeCourseBinding!!.viewPagerCourse.addOnPageChangeListener(this)
        if (fragmentTakeCourseBinding!!.viewPagerCourse.currentItem == 0) {
            fragmentTakeCourseBinding!!.previousStep.visibility = View.GONE
        }
        setCourseData()
        setListeners()
        fragmentTakeCourseBinding!!.viewPagerCourse.currentItem = position
    }

    private fun setListeners() {
        fragmentTakeCourseBinding!!.nextStep.setOnClickListener(this)
        fragmentTakeCourseBinding!!.previousStep.setOnClickListener(this)
        fragmentTakeCourseBinding!!.btnRemove.setOnClickListener(this)
        fragmentTakeCourseBinding!!.finishStep.setOnClickListener(this)
        fragmentTakeCourseBinding!!.courseProgress.setOnSeekBarChangeListener(object :
            OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                val currentProgress =
                    getCurrentProgress(steps!!, mRealm!!, userModel!!.id, courseId)
                if (b && i <= currentProgress + 1) {
                    fragmentTakeCourseBinding!!.viewPagerCourse.currentItem = i
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setCourseData() {
        fragmentTakeCourseBinding!!.tvStepTitle.text = currentCourse!!.courseTitle
        if (!currentCourse!!.userId!!.contains(userModel!!.id)) {
            fragmentTakeCourseBinding!!.btnRemove.visibility = View.VISIBLE
            fragmentTakeCourseBinding!!.btnRemove.text = getString(R.string.join)
            getAlertDialog(requireActivity(), getString(R.string.do_you_want_to_join_this_course), getString(R.string.join_this_course)) { _: DialogInterface?, _: Int -> addRemoveCourse() }
        } else {
            fragmentTakeCourseBinding!!.btnRemove.visibility = View.GONE
        }
        createActivity(mRealm!!, userModel!!, currentCourse!!)
        fragmentTakeCourseBinding!!.tvStep.text = getString(R.string.step) + " 0/" + steps!!.size
        if (steps != null) fragmentTakeCourseBinding!!.courseProgress.max = steps!!.size
        val i = getCurrentProgress(steps!!, mRealm!!, userModel!!.id, courseId)
        if (i < steps!!.size) fragmentTakeCourseBinding!!.courseProgress.secondaryProgress = i + 1
        fragmentTakeCourseBinding!!.courseProgress.progress = i
        fragmentTakeCourseBinding!!.courseProgress.visibility =
            if (currentCourse!!.userId!!.contains(userModel!!.id))
                View.VISIBLE
            else View.GONE
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    override fun onPageSelected(position: Int) {
        if (position > 0) {
            fragmentTakeCourseBinding!!.tvStepTitle.text = steps!![position - 1]!!.stepTitle
            Utilities.log("Po " + position + " " + steps!!.size)
            if (position - 1 < steps!!.size) changeNextButtonState(position)
        } else {
            fragmentTakeCourseBinding!!.nextStep.isClickable = true
            fragmentTakeCourseBinding!!.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
            fragmentTakeCourseBinding!!.tvStepTitle.text = currentCourse!!.courseTitle
        }
        val i = getCurrentProgress(steps!!, mRealm!!, userModel!!.id, courseId)
        if (i < steps!!.size) fragmentTakeCourseBinding!!.courseProgress.secondaryProgress = i + 1
        fragmentTakeCourseBinding!!.courseProgress.progress = i
        fragmentTakeCourseBinding!!.tvStep.text =
            String.format("Step %d/%d", position, steps!!.size)
    }

    private fun changeNextButtonState(position: Int) {
        Utilities.log(isStepCompleted(mRealm!!, steps!![position - 1]!!.id, userModel!!.id!!).toString() + " is step completed")
        if (isStepCompleted(mRealm!!, steps!![position - 1]!!.id, userModel!!.id!!) || !showBetaFeature(Constants.KEY_EXAM, requireContext())) {
            fragmentTakeCourseBinding!!.nextStep.isClickable = true
            fragmentTakeCourseBinding!!.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
        } else {
            fragmentTakeCourseBinding!!.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
            fragmentTakeCourseBinding!!.nextStep.isClickable = false
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        Utilities.log("State $state")
    }

    private fun onClickNext() {
        if (fragmentTakeCourseBinding!!.viewPagerCourse.currentItem == steps!!.size) {
            fragmentTakeCourseBinding!!.nextStep.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
            fragmentTakeCourseBinding!!.nextStep.visibility = View.GONE
            fragmentTakeCourseBinding!!.finishStep.visibility = View.VISIBLE
        }
    }

    private fun onClickPrevious() {
        if (fragmentTakeCourseBinding!!.viewPagerCourse.currentItem - 1 == 0) {
            fragmentTakeCourseBinding!!.previousStep.visibility = View.GONE
            fragmentTakeCourseBinding!!.nextStep.visibility = View.VISIBLE
            fragmentTakeCourseBinding!!.finishStep.visibility = View.GONE
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.next_step -> {
                if (isValidClickRight) {
                    fragmentTakeCourseBinding!!.viewPagerCourse.currentItem = fragmentTakeCourseBinding!!.viewPagerCourse.currentItem + 1
                    fragmentTakeCourseBinding!!.previousStep.visibility = View.VISIBLE
                }
                onClickNext()
            }

            R.id.previous_step -> {
                onClickPrevious()
                if (isValidClickLeft) {
                    fragmentTakeCourseBinding!!.viewPagerCourse.currentItem = fragmentTakeCourseBinding!!.viewPagerCourse.currentItem - 1
                }
            }

            R.id.finish_step -> requireActivity().supportFragmentManager.popBackStack()
            R.id.btn_remove -> addRemoveCourse()
        }
    }

    private fun addRemoveCourse() {
        if (!mRealm!!.isInTransaction) mRealm!!.beginTransaction()
        if (currentCourse!!.userId!!.contains(userModel!!.id)) {
            currentCourse!!.removeUserId(userModel!!.id)
            onRemove(mRealm!!, "courses", userModel!!.id!!, courseId!!)
        } else {
            currentCourse!!.setUserId(userModel!!.id)
            onAdd(mRealm!!, "courses", userModel!!.id!!, courseId!!)
        }
        Utilities.toast(activity, "Course " + (if (currentCourse!!.userId!!.contains(userModel!!.id)) getString(R.string.added_to) else getString(R.string.removed_from)) + " " + getString(
            R.string.my_courses
        ))
        setCourseData()
    }

    private val isValidClickRight: Boolean
        get() = fragmentTakeCourseBinding!!.viewPagerCourse.adapter != null && fragmentTakeCourseBinding!!.viewPagerCourse.currentItem < fragmentTakeCourseBinding!!.viewPagerCourse.adapter!!
            .count
    private val isValidClickLeft: Boolean
        get() = fragmentTakeCourseBinding!!.viewPagerCourse.adapter != null && fragmentTakeCourseBinding!!.viewPagerCourse.currentItem > 0

    companion object {
        @JvmStatic
        fun newInstance(b: Bundle?): TakeCourseFragment {
            val takeCourseFragment = TakeCourseFragment()
            takeCourseFragment.arguments = b
            return takeCourseFragment
        }
    }
}
