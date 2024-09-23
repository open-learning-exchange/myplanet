package org.ole.planet.myplanet.ui.dashboard

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Case
import io.realm.Realm
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerParentFragment
import org.ole.planet.myplanet.databinding.FragmentHomeBellBinding
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
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
        checkPendingSurveys()
    }

    private fun checkPendingSurveys() {
        val pendingSurveys = getPendingSurveys(user?.id, mRealm)

        if (pendingSurveys.isNotEmpty()) {
            val surveyTitles = getSurveyTitlesFromSubmissions(pendingSurveys, mRealm)  //Get surveyTitles from Submissions

            val dialogView = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_survey_list, null)
            val recyclerView: RecyclerView = dialogView.findViewById(R.id.recyclerViewSurveys)
            recyclerView.layoutManager = LinearLayoutManager(requireActivity())

            val alertDialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                .setTitle(getString(R.string.surveys_to_complete, pendingSurveys.size, if (pendingSurveys.size > 1) "surveys" else "survey"))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("survey"))
                    dialog.dismiss()
                }
                .create()

            val adapter = SurveyAdapter(surveyTitles, { position ->
                val selectedSurvey = pendingSurveys[position].id
                AdapterMySubmission.openSurvey(homeItemClickListener, selectedSurvey, true)
            }, alertDialog)

            recyclerView.adapter = adapter
            alertDialog.show()
            alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
        }
    }

    private fun getPendingSurveys(userId: String?, realm: Realm): List<RealmSubmission> {
        return realm.where(RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("type", "survey")
            .equalTo("status", "pending", Case.INSENSITIVE)
            .findAll()
    }

    private fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>, realm: Realm): List<String> {
        val titles = mutableListOf<String>()
        submissions.forEach { submission ->
            val exam = realm.where(RealmStepExam::class.java)
                .equalTo("id", submission.parentId)
                .findFirst()
            exam?.name?.let { titles.add(it) }
        }
        return titles
    }

    private fun showBadges() {
        fragmentHomeBellBinding.cardProfileBell.llBadges.removeAllViews()
        val completedCourses = getCompletedCourses(mRealm, user?.id)
        completedCourses.forEachIndexed { index, course ->
            val rootView = requireActivity().findViewById<ViewGroup>(android.R.id.content)
            val star = LayoutInflater.from(activity).inflate(R.layout.image_start, rootView, false) as ImageView
            setColor(course.courseId, star)
            fragmentHomeBellBinding.cardProfileBell.llBadges.addView(star)
            star.contentDescription = "${getString(R.string.completed_course)} ${course.courseTitle}"
            star.setOnClickListener {
                openCourse(course, index)
            }
        }
    }

    private fun getCompletedCourses(realm: Realm, userId: String?): List<RealmMyCourse> {
        val myCourses = RealmMyCourse.getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).findAll())
        val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)

        return myCourses.filter { course ->
            val progress = courseProgress[course.id]
            progress?.let {
                it.asJsonObject["current"].asInt == it.asJsonObject["max"].asInt
            } ?: false
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

    private fun setColor(courseId: String?, star: ImageView) {
        if (RealmCertification.isCourseCertified(mRealm, courseId)) {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        } else {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_blue_grey_300))
        }
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
    }

    private fun openHelperFragment(f: Fragment) {
        val b = Bundle()
        b.putBoolean("isMyCourseLib", true)
        f.arguments = b
        homeItemClickListener?.openCallFragment(f)
    }
}
