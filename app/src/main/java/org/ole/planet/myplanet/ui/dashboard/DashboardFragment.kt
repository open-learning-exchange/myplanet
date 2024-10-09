package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentHomeBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getNoOfSurveySubmissionByUser
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.ui.news.NewsFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.TimeUtils.currentDate

class DashboardFragment : BaseDashboardFragment() {
    private lateinit var fragmentHomeBinding: FragmentHomeBinding
    private lateinit var dRealm: Realm
    private lateinit var databaseService: DatabaseService
    var user: RealmUserModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false)
        val view: View = fragmentHomeBinding.root
        fragmentHomeBinding.cardProfile.tvSurveys.setOnClickListener {
            homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("survey"))
        }
        fragmentHomeBinding.cardProfile.tvNews.setOnClickListener {
            homeItemClickListener?.openCallFragment(NewsFragment())
        }
        fragmentHomeBinding.cardProfile.tvSubmission.setOnClickListener {
            homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("exam"))
        }
        fragmentHomeBinding.cardProfile.tvAchievement.visibility = View.VISIBLE
        fragmentHomeBinding.cardProfile.tvAchievement.setOnClickListener {
            homeItemClickListener?.openCallFragment(AchievementFragment())
        }
        databaseService = DatabaseService(requireActivity())
        dRealm = databaseService.realmInstance
        user = UserProfileDbHandler(requireContext()).userModel
        onLoaded(view)
        initView(view)
        (activity as AppCompatActivity?)?.supportActionBar?.subtitle = currentDate()
        return fragmentHomeBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val noOfSurvey = getNoOfSurveySubmissionByUser(settings?.getString("userId", "--"), dRealm)
        fragmentHomeBinding.cardProfile.imgSurveyWarn.visibility = if (noOfSurvey == 0) View.VISIBLE else View.GONE
        fragmentHomeBinding.addResource.setOnClickListener {
            if (user?.id?.startsWith("guest") == false) {
                AddResourceFragment().show(childFragmentManager, getString(R.string.add_res))
            } else {
                guestDialog(requireContext())
            }
        }
    }
}