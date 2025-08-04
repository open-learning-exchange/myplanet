package org.ole.planet.myplanet.ui.team

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMemberCount
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.syncTeamActivities
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ApplicantsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.CalendarPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ChatPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.CoursesPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.DocumentsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.FinancesPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.MembersPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.MissionPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.PlanPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ReportsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ResourcesPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.SurveyPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.TasksPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.TeamPage

@AndroidEntryPoint
class TeamDetailFragment : BaseTeamFragment(), MemberChangeListener {
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    
    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var uploadManager: UploadManager
    
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding
    private var directTeamName: String? = null
    private var directTeamType: String? = null
    private var directTeamId: String? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    private val serverUrlMapper = ServerUrlMapper()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""
    private var pageConfigs: List<TeamPageConfig> = emptyList()

    private fun buildPages(isMyTeam: Boolean): List<TeamPageConfig> {
        val isEnterprise = team?.type == "enterprise"
        val pages = mutableListOf<TeamPageConfig>()
        if (isMyTeam || team?.isPublic == true) {
            pages += ChatPage
            pages += if (isEnterprise) MissionPage else PlanPage
            pages += if (isEnterprise) TeamPage else MembersPage
            pages += TasksPage
            pages += CalendarPage
            pages += SurveyPage
            pages += if (isEnterprise) FinancesPage else CoursesPage
            if (isEnterprise) pages += ReportsPage
            pages += if (isEnterprise) DocumentsPage else ResourcesPage
            pages += if (isEnterprise) ApplicantsPage else JoinRequestsPage
        } else {
            pages += if (isEnterprise) MissionPage else PlanPage
            pages += if (isEnterprise) TeamPage else MembersPage
        }
        return pages
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        startTeamSync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        directTeamId = requireArguments().getString("teamId")
        directTeamName = requireArguments().getString("teamName")
        directTeamType = requireArguments().getString("teamType")

        val teamId = requireArguments().getString("id" ) ?: ""
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        val user = UserProfileDbHandler(requireContext()).userModel
        mRealm = userRepository.getRealm()

        if (shouldQueryRealm(teamId)) {
            if (teamId.isNotEmpty()) {
                team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                    ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            }
        } else {
            val effectiveTeamId = directTeamId ?: teamId
            if (effectiveTeamId.isNotEmpty()) {
                team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", effectiveTeamId).findFirst()
            }
        }

        setupTeamDetails(isMyTeam, user)

        return fragmentTeamDetailBinding.root
    }

    private fun startTeamSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && prefManager.isTeamsSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                activity?.runOnUiThread {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(requireContext().getString(R.string.syncing_team_data))
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        refreshTeamData()
                        prefManager.setTeamsSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null

                        Snackbar.make(fragmentTeamDetailBinding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                            .setAction("Retry") { startTeamSync() }
                            .show()
                    }
                }
            }
        }, "full", listOf("tasks", "meetups", "team_activities"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    private fun setupTeamDetails(isMyTeam: Boolean, user: RealmUserModel?) {
        fragmentTeamDetailBinding.root.post {
            if (isAdded && !requireActivity().isFinishing) {
                setupViewPager(isMyTeam)
            }
        }

        val pageId = arguments?.getString("navigateToPage")
        if (!pageId.isNullOrEmpty()) {
            fragmentTeamDetailBinding.root.post {
                val index = pageConfigs.indexOfFirst { it.id == pageId }
                if (index >= 0 && index < (fragmentTeamDetailBinding.viewPager2.adapter?.itemCount ?: 0)) {
                    fragmentTeamDetailBinding.viewPager2.currentItem = index
                }
            }
        }

        fragmentTeamDetailBinding.title.text = getEffectiveTeamName()
        fragmentTeamDetailBinding.subtitle.text = getEffectiveTeamType()

        if (!isMyTeam) {
            setupNonMyTeamButtons(user)
        } else {
            setupMyTeamButtons(user)
        }

        if(getJoinedMemberCount(team!!._id.toString(), mRealm) <= 1 && isMyTeam){
            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
        }
    }

    private fun setupViewPager(isMyTeam: Boolean) {
        pageConfigs = buildPages(isMyTeam)
        fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(
            requireActivity(),
            pageConfigs,
            team?._id,
            this
        )
        TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
            tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
        }.attach()
    }

    private fun setupNonMyTeamButtons(user: RealmUserModel?) {
        fragmentTeamDetailBinding.btnAddDoc.isEnabled = false
        fragmentTeamDetailBinding.btnAddDoc.visibility = View.GONE
        fragmentTeamDetailBinding.btnLeave.isEnabled = true
        fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE

        if (user?.id?.startsWith("guest") == true){
            fragmentTeamDetailBinding.btnLeave.isEnabled = false
            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
        }

        val currentTeam = team
        if (currentTeam != null && !currentTeam._id.isNullOrEmpty()) {
            val isUserRequested = currentTeam.requested(user?.id, mRealm)
            if (isUserRequested) {
                fragmentTeamDetailBinding.btnLeave.text = getString(R.string.requested)
                fragmentTeamDetailBinding.btnLeave.isEnabled = false
            } else {
                fragmentTeamDetailBinding.btnLeave.text = getString(R.string.join)
                fragmentTeamDetailBinding.btnLeave.setOnClickListener {
                    RealmMyTeam.requestToJoin(currentTeam._id!!, user, mRealm, team?.teamType)
                    fragmentTeamDetailBinding.btnLeave.text = getString(R.string.requested)
                    fragmentTeamDetailBinding.btnLeave.isEnabled = false
                    syncTeamActivities(requireContext(), uploadManager)
                }
            }
        } else {
            throw IllegalStateException("Team or team ID is null, cannot proceed.")
        }
    }

    private fun setupMyTeamButtons(user: RealmUserModel?) {
        fragmentTeamDetailBinding.btnAddDoc.isEnabled = true
        fragmentTeamDetailBinding.btnAddDoc.visibility = View.VISIBLE
        fragmentTeamDetailBinding.btnLeave.isEnabled = true
        fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE

        fragmentTeamDetailBinding.btnLeave.setOnClickListener {
            AlertDialog.Builder(requireContext()).setMessage(R.string.confirm_exit)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    team?.leave(user, mRealm)
                    Utilities.toast(activity, getString(R.string.left_team))
                    fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(
                        requireActivity(),
                        buildPages(false),
                        team?._id,
                        this
                    )
                    TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
                        tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
                    }.attach()
                    fragmentTeamDetailBinding.llActionButtons.visibility = View.GONE
                }.setNegativeButton(R.string.no, null).show()
        }

        fragmentTeamDetailBinding.btnAddDoc.setOnClickListener {
            MainApplication.showDownload = true
            val documentsIndex = pageConfigs.indexOf(DocumentsPage)
            if (documentsIndex != -1) {
                fragmentTeamDetailBinding.viewPager2.currentItem = documentsIndex
            }
            MainApplication.showDownload = false
            MainApplication.listener?.onAddDocument()
        }
    }

    private fun refreshTeamData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            val teamId = requireArguments().getString("id") ?: directTeamId ?: ""
            val isMyTeam = requireArguments().getBoolean("isMyTeam", false)

            if (teamId.isNotEmpty()) {
                val updatedTeam = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                if (updatedTeam != null) {
                    team = updatedTeam

                    pageConfigs = buildPages(isMyTeam)
                    fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(
                        requireActivity(),
                        pageConfigs,
                        team?._id,
                        this
                    )
                    TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
                        tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
                    }.attach()

                    fragmentTeamDetailBinding.title.text = getEffectiveTeamName()
                    fragmentTeamDetailBinding.subtitle.text = getEffectiveTeamType()

                    if(getJoinedMemberCount(team!!._id.toString(), mRealm) <= 1 && isMyTeam){
                        fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
                    } else {
                        fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onMemberChanged() {
        if(getJoinedMemberCount("${team?._id}", mRealm) <= 1){
            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
        } else{
            fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        createTeamLog()
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun createTeamLog() {
        val userModel = UserProfileDbHandler(requireContext()).userModel ?: return
        val userName = userModel.name
        val userPlanetCode = userModel.planetCode
        val userParentCode = userModel.parentCode
        val teamType = getEffectiveTeamType()

        CoroutineScope(Dispatchers.IO).launch {
            val realm = userRepository.getRealm()

            realm.executeTransaction { r ->
                val log = r.createObject(RealmTeamLog::class.java, "${UUID.randomUUID()}")
                log.teamId = getEffectiveTeamId()
                log.user = userName
                log.createdOn = userPlanetCode
                log.type = "teamVisit"
                log.teamType = teamType
                log.parentCode = userParentCode
                log.time = Date().time
            }

            realm.close()
        }
    }

    private fun shouldQueryRealm(teamId: String): Boolean {
        return teamId.isNotEmpty()
    }

    override fun onDestroy() {
        super.onDestroy()
        customProgressDialog?.dismiss()
        customProgressDialog = null
    }

    companion object {
        fun newInstance(
            teamId: String,
            teamName: String,
            teamType: String,
            isMyTeam: Boolean,
            navigateToPage: TeamPageConfig? = null
        ): TeamDetailFragment {
            val fragment = TeamDetailFragment()
            val args = Bundle().apply {
                putString("teamId", teamId)
                putString("teamName", teamName)
                putString("teamType", teamType)
                putBoolean("isMyTeam", isMyTeam)
                navigateToPage?.let { putString("navigateToPage", it.id) }
            }
            fragment.arguments = args
            return fragment
        }
    }
}
