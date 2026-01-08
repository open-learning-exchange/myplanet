package org.ole.planet.myplanet.ui.teams

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
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.BaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.callback.TeamUpdateListener
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMemberCount
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserSessionManager
import org.ole.planet.myplanet.service.sync.RealtimeSyncCoordinator
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.service.sync.SyncManager
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ApplicantsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.CalendarPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ChatPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.CoursesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.DocumentsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.FinancesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.MembersPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.MissionPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.PlanPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ReportsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ResourcesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.SurveyPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.TasksPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.TeamPage
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TeamDetailFragment : BaseTeamFragment(), MemberChangeListener, TeamUpdateListener {
    
    @Inject
    lateinit var userSessionManager: UserSessionManager
    
    @Inject
    lateinit var syncManager: SyncManager

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
    private var loadTeamJob: Job? = null

    private fun getCurrentUser(): RealmUserModel? {
        return userSessionManager.userModel
    }

    private fun detachCurrentUser(): RealmUserModel? {
        return userSessionManager.getUserModelCopy()
    }

    private fun pageIndexById(pageId: String?): Int? {
        pageId ?: return null
        val idx = pageConfigs.indexOfFirst { it.id == pageId }
        return if (idx >= 0) idx else null
    }

    private fun selectPage(pageId: String?, smoothScroll: Boolean = true) {
        val index = pageIndexById(pageId)
        index?.let {
            binding.viewPager2.setCurrentItem(it, smoothScroll)
        }
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
        val user = detachCurrentUser()

        binding.loadingIndicator?.visibility = View.VISIBLE
        binding.contentLayout?.visibility = View.GONE

        renderPlaceholder()

        loadTeamJob?.cancel()
        loadTeamJob = viewLifecycleOwner.lifecycleScope.launch {
            val resolvedTeam = when {
                shouldQueryRealm(teamId) && teamId.isNotEmpty() -> {
                    teamsRepository.getTeamByDocumentIdOrTeamId(teamId)
                }

                else -> {
                    val effectiveTeamId = (directTeamId ?: "").ifEmpty { teamId }
                    if (effectiveTeamId.isNotEmpty()) {
                        teamsRepository.getTeamById(effectiveTeamId)
                    } else {
                        null
                    }
                }
            }

            if (!isAdded) {
                return@launch
            }

            if (shouldQueryRealm(teamId) && resolvedTeam == null) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.no_team_available),
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }

            resolvedTeam?.let { team = it }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                binding.loadingIndicator?.visibility = View.GONE
                binding.contentLayout?.visibility = View.VISIBLE
                setupTeamDetails(isMyTeam, user)
                val targetPageId = arguments?.getString("navigateToPage") ?: team?._id?.let { teamLastPage[it] }
                setupViewPager(isMyTeam, targetPageId)
            }
            loadTeamJob = null
        }

        return binding.root
    }

    private fun renderPlaceholder() {
        binding.title.text = directTeamName ?: getString(R.string.loading_teams)
        binding.subtitle.text = directTeamType ?: ""
        binding.btnAddDoc.isEnabled = false
        binding.btnLeave.isEnabled = false
        binding.viewPager2.adapter = null
    }

    private fun startTeamSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && prefManager.isTeamsSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                updateServerIfNecessary(mapping)
            }
            startSyncManager()
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
                        refreshTeamDetails()
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
        binding.viewPager2.apply {
            isSaveEnabled = false
            offscreenPageLimit = 2
            isUserInputEnabled = true
            setPageTransformer { page, position ->
                page.alpha = 1.0f - kotlin.math.abs(position)
            }
        }

        if (binding.viewPager2.id == View.NO_ID) {
            binding.viewPager2.id = View.generateViewId()
        }

        binding.viewPager2.adapter = null
        binding.viewPager2.adapter = TeamPagerAdapter(
            requireActivity(),
            pageConfigs,
            team?._id,
            this,
            this
        )
        binding.tabLayout.tabMode = com.google.android.material.tabs.TabLayout.MODE_SCROLLABLE
        binding.tabLayout.isInlineLabel = true

        TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position ->
            val title = (binding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
            val pageConfig = pageConfigs.getOrNull(position)
            tab.text = title
        }.attach()

        selectPage(restorePageId, false)

        binding.viewPager2.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val pageConfig = pageConfigs.getOrNull(position)
                    val pageId = pageConfig?.id
                    team?._id?.let { teamId ->
                        pageId?.let {
                            teamLastPage[teamId] = it
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
                viewLifecycleOwner.lifecycleScope.launch {
                    val userId = user?.id
                    val userPlanetCode = user?.planetCode
                    val teamType = team?.teamType
                    teamsRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
                    binding.btnLeave.text = getString(R.string.requested)
                    binding.btnLeave.isEnabled = false
                    teamsRepository.syncTeamActivities()
                }
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

    private suspend fun refreshTeamDetails() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            val primaryTeamId = requireArguments().getString("id") ?: ""
            val fallbackTeamId = directTeamId ?: ""
            val isMyTeam = requireArguments().getBoolean("isMyTeam", false)

            val updatedTeam = when {
                primaryTeamId.isNotEmpty() -> teamsRepository.getTeamByDocumentIdOrTeamId(primaryTeamId)
                fallbackTeamId.isNotEmpty() -> teamsRepository.getTeamById(fallbackTeamId)
                else -> null
            }

            if (updatedTeam != null) {
                team = updatedTeam

                // Update arguments and direct variables with new team data
                directTeamName = updatedTeam.name
                directTeamType = updatedTeam.type
                requireArguments().apply {
                    putString("teamName", updatedTeam.name)
                    putString("teamType", updatedTeam.type)
                }

                val lastPageId = team?._id?.let { teamLastPage[it] } ?: arguments?.getString("navigateToPage")
                setupViewPager(isMyTeam, lastPageId)

                binding.title.text = updatedTeam.name
                binding.subtitle.text = updatedTeam.type

                team?._id?.let { id ->
                    if (getJoinedMemberCount(id, mRealm) <= 1 && isMyTeam) {
                        binding.btnLeave.visibility = View.GONE
                    } else {
                        binding.btnLeave.visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onMemberChanged() {
        _binding ?: return
        _binding?.let { binding ->
            val teamId = team?._id ?: return@let
            val joinedCount = getJoinedMemberCount(teamId, mRealm)
            binding.btnLeave.visibility = if (joinedCount <= 1) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }

    override fun onTeamDetailsUpdated() {
        viewLifecycleOwner.lifecycleScope.launch {
            refreshTeamDetails()
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
        val userModel = getCurrentUser() ?: return
        val userName = userModel.name
        val userPlanetCode = userModel.planetCode
        val userParentCode = userModel.parentCode
        val teamType = getEffectiveTeamType()

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                teamsRepository.logTeamVisit(
                    teamId = getEffectiveTeamId(),
                    userName = userName,
                    userPlanetCode = userPlanetCode,
                    userParentCode = userParentCode,
                    teamType = teamType,
                )
            }
        }
    }

    private fun setupRealtimeSync() {
        realtimeSyncListener = object : BaseRealtimeSyncListener() {
            override fun onTableDataUpdated(update: TableDataUpdate) {
                if (update.table == "teams" && update.shouldRefreshUI) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        refreshTeamDetails()
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
        loadTeamJob?.cancel()
        loadTeamJob = null
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
