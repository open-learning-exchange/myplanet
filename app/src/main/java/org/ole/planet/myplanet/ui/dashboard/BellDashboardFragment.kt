package org.ole.planet.myplanet.ui.dashboard

import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.databinding.*
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
import java.util.*

class BellDashboardFragment : BaseDashboardFragment() {
    private lateinit var binding: FragmentHomeBellBinding
    private lateinit var cardProfileBellBinding: CardProfileBellBinding
    private lateinit var homeCardTeamsBinding: HomeCardTeamsBinding
    private lateinit var homeCardLibraryBinding: HomeCardLibraryBinding
    private lateinit var homeCardCoursesBinding: HomeCardCoursesBinding
    private lateinit var homeCardMylifeBinding: HomeCardMylifeBinding


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBellBinding.inflate(inflater, container, false)
        cardProfileBellBinding = CardProfileBellBinding.bind(cardProfileBellBinding.root)
        homeCardTeamsBinding = HomeCardTeamsBinding.bind(homeCardTeamsBinding.root)
        homeCardLibraryBinding = HomeCardLibraryBinding.bind(homeCardLibraryBinding.root)
        homeCardCoursesBinding = HomeCardCoursesBinding.bind(homeCardCoursesBinding.root)
        homeCardMylifeBinding = HomeCardMylifeBinding.bind(homeCardMylifeBinding.root)
        declareElements()

        return (binding.root)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        cardProfileBellBinding.txtDate.text = TimeUtils.formatDate(Date().time)
        cardProfileBellBinding.txtCommunityName.text = model.planetCode
        (activity as DashboardActivity?)?.supportActionBar?.hide()
        binding.addResource.setOnClickListener { v: View? ->
            AddResourceFragment().show(
                childFragmentManager,
                "Add Resource"
            )
        }
        showBadges()
        if (!model.id.startsWith("guest") && TextUtils.isEmpty(model.key) && MainApplication.showHealthDialog) {
            AlertDialog.Builder(requireActivity())
                .setMessage("Health record not available, Sync health data?")
                .setPositiveButton("Sync") { _: DialogInterface?, i: Int ->
                    syncKeyId()
                    MainApplication.showHealthDialog = false
                }.setNegativeButton("Cancel", null).show()
        }
        // forceDownloadNewsImages();
    }

    private fun showBadges() {
        cardProfileBellBinding.llBadges.removeAllViews()
        val list = RealmCourseProgress.getPassedCourses(
            mRealm,
            BaseResourceFragment.settings.getString("userId", "")
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
            star.setColorFilter(resources.getColor(R.color.colorPrimary))
        } else {
            star.setColorFilter(resources.getColor(R.color.md_blue_grey_300))
        }

    private fun declareElements() {
        binding.apply {
            initView(root)
            homeCardTeamsBinding.llHomeTeam.setOnClickListener {
                homeItemClickListener.openCallFragment(
                    TeamFragment()
                )
            }
            homeCardLibraryBinding.myLibraryImageButton.setOnClickListener {
                openHelperFragment(
                    LibraryFragment()
                )
            }
            homeCardCoursesBinding.myCoursesImageButton.setOnClickListener {
                openHelperFragment(
                    CourseFragment()
                )
            }
            fabMyProgress.setOnClickListener { openHelperFragment(MyProgressFragment()) }
            fabMyActivity.setOnClickListener { openHelperFragment(MyActivityFragment()) }
            fabSurvey.setOnClickListener { openHelperFragment(SurveyFragment()) }
            cardProfileBellBinding.fabFeedback.setOnClickListener {
                openHelperFragment(
                    FeedbackListFragment()
                )
            }
            homeCardMylifeBinding.myLifeImageButton.setOnClickListener {
                homeItemClickListener.openCallFragment(
                    LifeFragment()
                )
            }
            fabNotification.setOnClickListener { showNotificationFragment() }
        }
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