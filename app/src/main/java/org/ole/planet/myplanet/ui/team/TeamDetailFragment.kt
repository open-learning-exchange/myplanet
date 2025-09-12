package org.ole.planet.myplanet.ui.team

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.BaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
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
import org.ole.planet.myplanet.service.sync.RealtimeSyncCoordinator
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
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TeamDetailFragment : BaseTeamFragment(), MemberChangeListener {
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    
    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var uploadManager: UploadManager
    
    private val syncCoordinator = RealtimeSyncCoordinator.getInstance()
    private lateinit var realtimeSyncListener: BaseRealtimeSyncListener

    private var _binding: FragmentTeamDetailBinding? = null
    private val binding get() = _binding!!
    private var directTeamName: String? = null
    private var directTeamType: String? = null
    private var directTeamId: String? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    private val serverUrlMapper = ServerUrlMapper()
    private val teamLastPage = mutableMapOf<String, String>()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""
    private var pageConfigs: List<TeamPageConfig> = emptyList()

    private fun pageIndexById(pageId: String?): Int? {
        pageId ?: return null
        val idx = pageConfigs.indexOfFirst { it.id == pageId }
        return if (idx >= 0) idx else null
    }

    private fun selectPage(pageId: String?, smoothScroll: Boolean = false) {
        pageIndexById(pageId)?.let { binding.viewPager2.setCurrentItem(it, smoothScroll) }
    }

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
        _binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        directTeamId = requireArguments().getString("teamId")
        directTeamName = requireArguments().getString("teamName")
        directTeamType = requireArguments().getString("teamType")

        val teamId = requireArguments().getString("id" ) ?: ""
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        val user = UserProfileDbHandler(requireContext()).userModel
        mRealm = databaseService.realmInstance

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

        return binding.root
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
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(requireContext().getString(R.string.syncing_team_data))
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        refreshTeamData()
                        prefManager.setTeamsSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null

                        Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
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
        binding.root.post {
            if (isAdded && !requireActivity().isFinishing) {
                val targetPageId = arguments?.getString("navigateToPage")
                    ?: team?._id?.let { teamLastPage[it] }
                setupViewPager(isMyTeam, targetPageId)
            }
        }

        binding.title.text = getEffectiveTeamName()
        binding.subtitle.text = getEffectiveTeamType()

        if (!isMyTeam) {
            setupNonMyTeamButtons(user)
        } else {
            setupMyTeamButtons(user)
        }

        team?._id?.let { id ->
            if (getJoinedMemberCount(id, mRealm) <= 1 && isMyTeam) {
                binding.btnLeave.visibility = View.GONE
            }
        }
    }

    private fun setupViewPager(isMyTeam: Boolean, restorePageId: String? = null) {
        pageConfigs = buildPages(isMyTeam)
        binding.viewPager2.isSaveEnabled = true
        binding.viewPager2.id = View.generateViewId()
        binding.viewPager2.adapter = TeamPagerAdapter(
            requireActivity(),
            pageConfigs,
            team?._id,
            this
        )
        TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position ->
            tab.text = (binding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
        }.attach()

        selectPage(restorePageId, false)

        binding.viewPager2.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    team?._id?.let { teamId ->
                        pageConfigs.getOrNull(position)?.id?.let { pageId ->
                            teamLastPage[teamId] = pageId
                        }
                    }
                }
            }
        )
    }

    private fun setupNonMyTeamButtons(user: RealmUserModel?) {
        binding.btnAddDoc.isEnabled = false
        binding.btnAddDoc.visibility = View.GONE
        binding.btnLeave.isEnabled = true
        binding.btnLeave.visibility = View.VISIBLE

        if (user?.id?.startsWith("guest") == true){
            binding.btnLeave.isEnabled = false
            binding.btnLeave.visibility = View.GONE
        }

        val currentTeam = team
        val teamId = currentTeam?._id
        if (teamId.isNullOrEmpty()) {
            Utilities.toast(activity, getString(R.string.no_team_available))
            return
        }
        val isUserRequested = currentTeam.requested(user?.id, mRealm)
        if (isUserRequested) {
            binding.btnLeave.text = getString(R.string.requested)
            binding.btnLeave.isEnabled = false
        } else {
            binding.btnLeave.text = getString(R.string.join)
            binding.btnLeave.setOnClickListener {
                RealmMyTeam.requestToJoin(teamId, user, mRealm, team?.teamType)
                binding.btnLeave.text = getString(R.string.requested)
                binding.btnLeave.isEnabled = false
                syncTeamActivities(requireContext(), uploadManager)
            }
        }
    }

    private fun setupMyTeamButtons(user: RealmUserModel?) {
        binding.btnAddDoc.isEnabled = true
        binding.btnAddDoc.visibility = View.VISIBLE
        binding.btnLeave.isEnabled = true
        binding.btnLeave.visibility = View.VISIBLE

        binding.btnLeave.setOnClickListener {
            AlertDialog.Builder(requireContext()).setMessage(R.string.confirm_exit)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    team?.leave(user, mRealm)
                    Utilities.toast(activity, getString(R.string.left_team))
                    val lastPageId = team?._id?.let { teamLastPage[it] } ?: arguments?.getString("navigateToPage")
                    setupViewPager(false, lastPageId)
                    binding.llActionButtons.visibility = View.GONE
                }.setNegativeButton(R.string.no, null).show()
        }

        binding.btnAddDoc.setOnClickListener {
            MainApplication.showDownload = true
            selectPage(DocumentsPage.id)
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
                    val lastPageId = team?._id?.let { teamLastPage[it] } ?: arguments?.getString("navigateToPage")
                    setupViewPager(isMyTeam, lastPageId)

                    binding.title.text = getEffectiveTeamName()
                    binding.subtitle.text = getEffectiveTeamType()

                    team?._id?.let { id ->
                        if (getJoinedMemberCount(id, mRealm) <= 1 && isMyTeam) {
                            binding.btnLeave.visibility = View.GONE
                        } else {
                            binding.btnLeave.visibility = View.VISIBLE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onMemberChanged() {
        if(getJoinedMemberCount("${team?._id}", mRealm) <= 1){
            binding.btnLeave.visibility = View.GONE
        } else{
            binding.btnLeave.visibility = View.VISIBLE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRealtimeSync()
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

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            databaseService.executeTransactionAsync { r ->
                val log = r.createObject(RealmTeamLog::class.java, "${UUID.randomUUID()}")
                log.teamId = getEffectiveTeamId()
                log.user = userName
                log.createdOn = userPlanetCode
                log.type = "teamVisit"
                log.teamType = teamType
                log.parentCode = userParentCode
                log.time = Date().time
            }
        }
    }

    private fun setupRealtimeSync() {
        realtimeSyncListener = object : BaseRealtimeSyncListener() {
            override fun onTableDataUpdated(update: TableDataUpdate) {
                if (update.table == "teams" && update.shouldRefreshUI) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        refreshTeamData()
                    }
                }
            }
        }
        syncCoordinator.addListener(realtimeSyncListener)
    }

    private fun shouldQueryRealm(teamId: String): Boolean {
        return teamId.isNotEmpty()
    }

    override fun onDestroyView() {
        if (::realtimeSyncListener.isInitialized) {
            syncCoordinator.removeListener(realtimeSyncListener)
        }
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
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
