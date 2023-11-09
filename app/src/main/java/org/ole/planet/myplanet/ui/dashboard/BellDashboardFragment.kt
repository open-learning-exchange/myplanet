package org.ole.planet.myplanet.ui.dashboard

import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentHomeBellBinding
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentHomeBellBinding = FragmentHomeBellBinding.inflate(inflater, container, false)

        val view = fragmentHomeBellBinding.root
        initView(view)
        declareElements()
        onLoaded(view)
        return fragmentHomeBellBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentHomeBellBinding.cardProfileBell.txtDate.text = TimeUtils.formatDate(Date().time)
        fragmentHomeBellBinding.cardProfileBell.txtCommunityName.text = model.planetCode
        (activity as DashboardActivity?)?.supportActionBar?.hide()
        fragmentHomeBellBinding.addResource.setOnClickListener {
            AddResourceFragment().show(
                childFragmentManager, getString(R.string.add_res)
            )
        }
        showBadges()
    }

    private fun showBadges() {
        fragmentHomeBellBinding.cardProfileBell.llBadges.removeAllViews()
        val list = RealmCourseProgress.getPassedCourses(mRealm, settings.getString("userId", ""))
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
            fragmentHomeBellBinding.cardProfileBell.llBadges.addView(star)
        }
    }

    private fun setColor(questions: Long, courseId: String, star: ImageView) =
        if (RealmCertification.isCourseCertified(mRealm, courseId)) {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        } else {
            star.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_blue_grey_300))
        }

    private fun declareElements() {
        fragmentHomeBellBinding.homeCardTeams.llHomeTeam.setOnClickListener { homeItemClickListener.openCallFragment(TeamFragment()) }
        fragmentHomeBellBinding.homeCardLibrary.myLibraryImageButton.setOnClickListener { openHelperFragment(LibraryFragment()) }
        fragmentHomeBellBinding.homeCardCourses.myCoursesImageButton.setOnClickListener { openHelperFragment(CourseFragment()) }
        fragmentHomeBellBinding.fabMyProgress.setOnClickListener { openHelperFragment(MyProgressFragment()) }
        fragmentHomeBellBinding.fabMyActivity.setOnClickListener { openHelperFragment(MyActivityFragment()) }
        fragmentHomeBellBinding.fabSurvey.setOnClickListener { openHelperFragment(SurveyFragment()) }
        fragmentHomeBellBinding.cardProfileBell.fabFeedback.setOnClickListener { openHelperFragment(FeedbackListFragment()) }
        fragmentHomeBellBinding.homeCardMyLife.myLifeImageButton.setOnClickListener { homeItemClickListener.openCallFragment(LifeFragment()) }
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