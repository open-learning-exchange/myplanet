package org.ole.planet.myplanet.ui.dashboard

//import kotlinx.android.synthetic.main.card_profile_bell.ll_badges
//import kotlinx.android.synthetic.main.card_profile_bell.txt_community_name
//import kotlinx.android.synthetic.main.card_profile_bell.txt_date
//import kotlinx.android.synthetic.main.card_profile_bell.view.fab_feedback
//import kotlinx.android.synthetic.main.fragment_home_bell.add_resource
//import kotlinx.android.synthetic.main.fragment_home_bell.view.fab_my_activity
//import kotlinx.android.synthetic.main.fragment_home_bell.view.fab_my_progress
//import kotlinx.android.synthetic.main.fragment_home_bell.view.fab_notification
//import kotlinx.android.synthetic.main.fragment_home_bell.view.fab_survey
//import kotlinx.android.synthetic.main.home_card_courses.view.myCoursesImageButton
//import kotlinx.android.synthetic.main.home_card_library.view.myLibraryImageButton
//import kotlinx.android.synthetic.main.home_card_mylife.view.myLifeImageButton
//import kotlinx.android.synthetic.main.home_card_teams.view.ll_home_team
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.databinding.CardProfileBellBinding
import org.ole.planet.myplanet.databinding.FragmentHomeBellBinding
import org.ole.planet.myplanet.databinding.HomeCardCoursesBinding
import org.ole.planet.myplanet.databinding.HomeCardLibraryBinding
import org.ole.planet.myplanet.databinding.HomeCardMylifeBinding
import org.ole.planet.myplanet.databinding.HomeCardTeamsBinding
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.ui.course.CourseFragment
import org.ole.planet.myplanet.ui.course.MyProgressFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.library.AddResourceFragment
import org.ole.planet.myplanet.ui.library.LibraryFragment
import org.ole.planet.myplanet.ui.mylife.LifeFragment
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.utilities.TimeUtils
import java.util.Date

class BellDashboardFragment : BaseDashboardFragment() {
    private lateinit var fragmentHomeBellBinding: FragmentHomeBellBinding
    private lateinit var cardProfileBellBinding: CardProfileBellBinding
    private lateinit var homeCardTeamsBinding: HomeCardTeamsBinding
    private lateinit var homeCardCoursesBinding: HomeCardCoursesBinding
    private lateinit var homeCardLibraryBinding: HomeCardLibraryBinding
    private lateinit var homeCardMylifeBinding: HomeCardMylifeBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentHomeBellBinding = FragmentHomeBellBinding.inflate(inflater, container, false)
        cardProfileBellBinding = CardProfileBellBinding.inflate(layoutInflater)
//        homeCardTeamsBinding = HomeCardTeamsBinding.inflate(layoutInflater)
        homeCardCoursesBinding = HomeCardCoursesBinding.inflate(layoutInflater)
        homeCardLibraryBinding = HomeCardLibraryBinding.inflate(layoutInflater)
        homeCardMylifeBinding = HomeCardMylifeBinding.inflate(layoutInflater)

        val view = fragmentHomeBellBinding.root

        declareElements(view)
        onLoaded(view)
        return fragmentHomeBellBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cardProfileBellBinding.txtDate.text = TimeUtils.formatDate(Date().time)
        cardProfileBellBinding.txtCommunityName.text = model.planetCode
        (activity as DashboardActivity?)?.supportActionBar?.hide()
        fragmentHomeBellBinding.addResource.setOnClickListener {
            AddResourceFragment().show(
                childFragmentManager, getString(R.string.add_res)
            )
        }
        showBadges()
        if (!model.id.startsWith("guest") && TextUtils.isEmpty(model.key) && MainApplication.showHealthDialog) {
            AlertDialog.Builder(activity!!)
                .setMessage(getString(R.string.health_record_not_available_sync_health_data))
                .setPositiveButton(getString(R.string.sync)) { _: DialogInterface?, _: Int ->
                    syncKeyId()
                    MainApplication.showHealthDialog = false
                }.setNegativeButton(getString(R.string.cancel), null).show()
        }
    }

    private fun showBadges() {
        cardProfileBellBinding.llBadges.removeAllViews()
        val list = RealmCourseProgress.getPassedCourses(
            mRealm, BaseResourceFragment.settings.getString("userId", "")
        )
        for (sub in list) {
            val star =
                LayoutInflater.from(activity).inflate(R.layout.image_start, null) as ImageView
            val examId = if (sub.parentId.contains("@")) sub.parentId.split("@")
                .toTypedArray()[0] else sub.parentId
            val courseId =
                if (sub.parentId.contains("@")) sub.parentId.split("@").toTypedArray()[1] else ""
            val questions =
                mRealm.where(RealmExamQuestion::class.java).equalTo("examId", examId).count()
            setColor(questions, courseId, star)
            cardProfileBellBinding.llBadges.addView(star)
        }
    }

    private fun setColor(questions: Long, courseId: String, star: ImageView) =
        if (RealmCertification.isCourseCertified(mRealm, courseId)) {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        } else {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_blue_grey_300))
        }

    private fun declareElements(view: View) {
        initView(view)
        val cardProfileView: View = cardProfileBellBinding.root
        homeCardTeamsBinding = HomeCardTeamsBinding.inflate(layoutInflater)
        val homeCardTeamsView: View = homeCardTeamsBinding.root
        homeCardTeamsView.findViewById<View>(R.id.ll_home_team).setOnClickListener {
            Log.d("clicked", "clicked")
            homeItemClickListener.openCallFragment(TeamFragment())
        }
        homeCardLibraryBinding.myLibraryImageButton.setOnClickListener { openHelperFragment(LibraryFragment()) }
        homeCardCoursesBinding.myCoursesImageButton.setOnClickListener { openHelperFragment(CourseFragment()) }
        fragmentHomeBellBinding.fabMyProgress.setOnClickListener { openHelperFragment(MyProgressFragment()) }
        fragmentHomeBellBinding.fabMyActivity.setOnClickListener { openHelperFragment(MyActivityFragment()) }
        fragmentHomeBellBinding.fabSurvey.setOnClickListener { openHelperFragment(SurveyFragment()) }
        view.findViewById<View>(R.id.fab_feedback).setOnClickListener { openHelperFragment(FeedbackListFragment()) }
        homeCardMylifeBinding.myLifeImageButton.setOnClickListener {
            homeItemClickListener.openCallFragment(
                LifeFragment()
            )
        }
        fragmentHomeBellBinding.fabNotification.setOnClickListener { showNotificationFragment() }
    }

    private fun openHelperFragment(f: Fragment) {
        val b = Bundle()
        b.putBoolean("isMyCourseLib", true)
        f.arguments = b
        homeItemClickListener.openCallFragment(f)
    }

    companion object {
        const val PREFS_NAME = "OLE_PLANET"
    }
}