package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.realm.Realm
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentHomeBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.news.NewsFragment
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.TimeUtils.currentDate

class DashboardFragment : BaseDashboardFragment() {
    private val viewModel: DashboardViewModel by viewModels()
    private var binding: FragmentHomeBinding? = null
    private lateinit var dRealm: Realm
    var user: RealmUserModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view: View = binding!!.root
        binding?.cardProfile?.tvSurveys?.setOnClickListener {
            homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("survey"))
        }
        binding?.cardProfile?.tvNews?.setOnClickListener {
            homeItemClickListener?.openCallFragment(NewsFragment())
        }
        binding?.cardProfile?.tvSubmission?.setOnClickListener {
            homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("exam"))
        }
        binding?.cardProfile?.tvAchievement?.visibility = View.VISIBLE
        binding?.cardProfile?.tvAchievement?.setOnClickListener {
            homeItemClickListener?.openCallFragment(AchievementFragment())
        }
        dRealm = databaseService.realmInstance
        user = UserProfileDbHandler(requireContext()).userModel
        onLoaded(view)
        initView(view)
        (activity as AppCompatActivity?)?.supportActionBar?.subtitle = currentDate()
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadDashboardData(settings?.getString("userId", "--"))
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.surveyWarning.collect { show ->
                    binding?.cardProfile?.imgSurveyWarn?.visibility =
                        if (show) View.VISIBLE else View.GONE
                }
            }
        }
        binding?.addResource?.setOnClickListener {
            if (user?.id?.startsWith("guest") == false) {
                AddResourceFragment().show(childFragmentManager, getString(R.string.add_res))
            } else {
                guestDialog(requireContext())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onDestroy() {
        dRealm.close()
        super.onDestroy()
    }
}
