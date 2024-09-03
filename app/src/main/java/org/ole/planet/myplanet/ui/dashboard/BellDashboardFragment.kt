package org.ole.planet.myplanet.ui.dashboard

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.realm.Case
import io.realm.Realm
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerParentFragment
import org.ole.planet.myplanet.databinding.FragmentHomeBellBinding
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getCourseByCourseId
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getCourseSteps
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.courses.MyProgressFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.mylife.LifeFragment
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.NetworkUtils.coroutineScope
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utilities.TimeUtils
import java.util.Date

class BellDashboardFragment : BaseDashboardFragment() {
    private lateinit var fragmentHomeBellBinding: FragmentHomeBellBinding
    var user: RealmUserModel? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentHomeBellBinding = FragmentHomeBellBinding.inflate(inflater, container, false)
        user = UserProfileDbHandler(requireContext()).userModel
        val view = fragmentHomeBellBinding.root
        initView(view)
        declareElements()
        onLoaded(view)
        return fragmentHomeBellBinding.root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentHomeBellBinding.cardProfileBell.txtDate.text = TimeUtils.formatDate(Date().time, "")
        fragmentHomeBellBinding.cardProfileBell.txtCommunityName.text = model?.planetCode
        isNetworkConnectedFlow.onEach { isConnected ->
            if (isConnected) {
                fragmentHomeBellBinding.cardProfileBell.imageView.borderColor = ContextCompat.getColor(requireActivity(), R.color.md_yellow_600)
                val serverUrl = settings?.getString("serverURL", "")
                if (!serverUrl.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        val canReachServer = withContext(Dispatchers.IO) {
                            isServerReachable(serverUrl)
                        }
                        if (canReachServer) {
                            fragmentHomeBellBinding.cardProfileBell.imageView.borderColor = ContextCompat.getColor(requireActivity(), R.color.green)
                        }
                    }
                }
            } else {
                fragmentHomeBellBinding.cardProfileBell.imageView.borderColor = ContextCompat.getColor(requireActivity(), R.color.md_red_700)
            }
        }.launchIn(coroutineScope)

        (activity as DashboardActivity?)?.supportActionBar?.hide()
        fragmentHomeBellBinding.addResource.setOnClickListener {
            if (user?.id?.startsWith("guest") == false) {
                AddResourceFragment().show(childFragmentManager, getString(R.string.add_res))
            } else {
                guestDialog(requireContext())
            }
        }
        showBadges()
        
        val noOfSurvey = RealmSubmission.getNoOfSurveySubmissionByUser(model?.id, mRealm)
        if (noOfSurvey >= 1){
            val title: String = if (noOfSurvey > 1 ) {
                "surveys"
            } else{
                "survey"
            }
            val itemsQuery = mRealm.where(RealmSubmission::class.java).equalTo("userId", model?.id)
                .equalTo("type", "survey").equalTo("status", "pending", Case.INSENSITIVE)
                .findAll()
            val courseTitles = itemsQuery.map { it.parent }
            val surveyNames = courseTitles.map { json ->
                try {
                    val jsonObject = json?.let { JSONObject(it) }
                    jsonObject?.getString("name")
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            val titleView = TextView(requireActivity()).apply {
                text = getString(R.string.surveys_to_complete, noOfSurvey, title)
                setTextColor(context.getColor(R.color.daynight_textColor))
                setPadding(90, 70, 0, 0)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            }
            val alertDialog: AlertDialog.Builder = AlertDialog.Builder(requireActivity())
            alertDialog.setCustomTitle(titleView)
            val surveyNamesArray = surveyNames.filterNotNull().map { it as CharSequence }.toTypedArray()
            alertDialog.setItems(surveyNamesArray) { _, which ->
                val selectedSurvey = itemsQuery[which]?.id
                AdapterMySubmission.openSurvey(homeItemClickListener, selectedSurvey, true)
            }
            alertDialog.setPositiveButton("OK") { dialog, _ ->
                homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("survey"))
                dialog.dismiss()
            }
            val dialog = alertDialog.create()
            dialog.show()
            dialog.window?.setBackgroundDrawableResource(R.color.card_bg)
        }
    }

    private fun showBadges() {
        fragmentHomeBellBinding.cardProfileBell.llBadges.removeAllViews()
        val courseCount = countCourseIds(mRealm)

        for ((index, entry) in courseCount.withIndex()) {
            val star = LayoutInflater.from(activity).inflate(R.layout.image_start, null) as ImageView
            val courseId = entry.keys.first()
            val count = entry.values.first()
            val steps = getCourseSteps(mRealm, courseId)
            if (count.toInt() == steps.size) {
                setColor(courseId, star)
                fragmentHomeBellBinding.cardProfileBell.llBadges.addView(star)
                star.setOnClickListener {
                    val course = getCourseByCourseId(courseId, mRealm)
                    star.contentDescription = "${getString(R.string.completed_course)} ${course?.courseTitle}"
                    openCourse(course, index)
                }
            }
        }
    }

    private fun openCourse(realmMyCourses: RealmMyCourse?, position: Int) {
        if (homeItemClickListener != null) {
            val f: Fragment = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", realmMyCourses?.courseId)
            b.putInt("position", position)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    private fun countCourseIds(mRealm: Realm): List<Map<String, Long>> {
        val courseIdCounts: MutableMap<String, Long> = HashMap()
        val results = mRealm.where(RealmCourseProgress::class.java).findAll()
        for (progress in results) {
            val courseId = progress.courseId
            if (courseId != null) {
                if (courseIdCounts.containsKey(courseId)) {
                    courseIdCounts[courseId] = courseIdCounts[courseId]!! + 1
                } else {
                    courseIdCounts[courseId] = 1
                }
            }
        }
        return courseIdCounts.map { mapOf(it.key to it.value) }
    }

    private fun setColor(courseId: String, star: ImageView) =
        if (RealmCertification.isCourseCertified(mRealm, courseId)) {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        } else {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_blue_grey_300))
        }

    private fun declareElements() {
        fragmentHomeBellBinding.homeCardTeams.llHomeTeam.setOnClickListener { homeItemClickListener?.openCallFragment(TeamFragment()) }
        fragmentHomeBellBinding.homeCardLibrary.myLibraryImageButton.setOnClickListener {
            if (user?.id?.startsWith("guest") == true) {
                guestDialog(requireContext())
            } else {
                openHelperFragment(ResourcesFragment())
            }
        }
        fragmentHomeBellBinding.homeCardCourses.myCoursesImageButton.setOnClickListener {
            if (user?.id?.startsWith("guest") == true) {
                guestDialog(requireContext())
            } else {
                openHelperFragment(CoursesFragment())
            }
        }
        fragmentHomeBellBinding.fabMyProgress.setOnClickListener { openHelperFragment(MyProgressFragment()) }
        fragmentHomeBellBinding.fabMyActivity.setOnClickListener { openHelperFragment(MyActivityFragment()) }
        fragmentHomeBellBinding.fabSurvey.setOnClickListener {
            BaseRecyclerParentFragment.isSurvey = true
            openHelperFragment(SurveyFragment())
        }
        fragmentHomeBellBinding.cardProfileBell.fabFeedback.setOnClickListener { openHelperFragment(FeedbackListFragment()) }
        fragmentHomeBellBinding.homeCardMyLife.myLifeImageButton.setOnClickListener { homeItemClickListener?.openCallFragment(LifeFragment()) }
        fragmentHomeBellBinding.fabNotification.setOnClickListener { showNotificationFragment() }
    }

    private fun openHelperFragment(f: Fragment) {
        val b = Bundle()
        b.putBoolean("isMyCourseLib", true)
        f.arguments = b
        homeItemClickListener?.openCallFragment(f)
    }
}