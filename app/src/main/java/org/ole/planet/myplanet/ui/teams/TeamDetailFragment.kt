package org.ole.planet.myplanet.ui.teams

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.callback.OnMemberChangeListener
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.ole.planet.myplanet.callback.OnTeamUpdateListener
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.CalendarPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ChatPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.CoursesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.DocumentsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.FinancesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.MembersPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.MissionPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.PlanPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ReportsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ResourcesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.SurveyPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.TasksPage
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class TeamDetailFragment : BaseTeamFragment(), OnMemberChangeListener, OnTeamUpdateListener {
    
    @Inject
    lateinit var userSessionManager: UserSessionManager
    
    private val syncManagerInstance = RealtimeSyncManager.getInstance()

    private var _binding: FragmentTeamDetailBinding? = null
    private val binding get() = _binding!!
    private var directTeamName: String? = null
    private var directTeamType: String? = null
    private var directTeamId: String? = null
    private val teamLastPage = mutableMapOf<String, String>()
    private var pageConfigs: List<TeamPageConfig> = emptyList()
    private var loadTeamJob: Job? = null

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
            pages += MembersPage
            pages += TasksPage
            pages += CalendarPage
            pages += SurveyPage
            pages += if (isEnterprise) FinancesPage else CoursesPage
            if (isEnterprise) pages += ReportsPage
            pages += if (isEnterprise) DocumentsPage else ResourcesPage
        } else {
            pages += if (isEnterprise) MissionPage else PlanPage
            pages += MembersPage
        }
        return pages
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        directTeamId = requireArguments().getString("teamId")
        directTeamName = requireArguments().getString("teamName")
        directTeamType = requireArguments().getString("teamType")

        val teamId = requireArguments().getString("id" ) ?: ""
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)

        binding.loadingIndicator?.visibility = View.VISIBLE
        binding.contentLayout?.visibility = View.GONE

        renderPlaceholder()

        loadTeamJob?.cancel()
        loadTeamJob = viewLifecycleOwner.lifecycleScope.launch {
            val user = userSessionManager.getUserModel()
            val resolvedTeam = when {
                shouldQueryRealm(teamId) && teamId.isNotEmpty() -> {
                    teamsRepository.getTeamByIdOrTeamId(teamId)
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

            val hasPendingRequest = team?._id?.let {
                teamsRepository.hasPendingRequest(it, user?.id)
            } ?: false

            binding.loadingIndicator?.visibility = View.GONE
            binding.contentLayout?.visibility = View.VISIBLE
            setupTeamDetails(isMyTeam, user, hasPendingRequest)
            val targetPageId = arguments?.getString("navigateToPage") ?: team?._id?.let { teamLastPage[it] }
            setupViewPager(isMyTeam, targetPageId)

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

    private fun setupTeamDetails(isMyTeam: Boolean, user: RealmUser?, hasPendingRequest: Boolean) {
        binding.title.text = getEffectiveTeamName()
        binding.subtitle.text = getEffectiveTeamType()

        if (!isMyTeam) {
            setupNonMyTeamButtons(user, hasPendingRequest)
        } else {
            setupMyTeamButtons(user)
        }

        team?._id?.let { id ->
            viewLifecycleOwner.lifecycleScope.launch {
                val memberCount = teamsRepository.getJoinedMemberCount(id)
                if (memberCount <= 1 && isMyTeam) {
                    binding.btnLeave.visibility = View.GONE
                }
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

        val currentAdapter = binding.viewPager2.adapter as? TeamPagerAdapter
        if (currentAdapter != null) {
            currentAdapter.updatePages(pageConfigs)
        } else {
            binding.viewPager2.adapter = TeamPagerAdapter(
                this, pageConfigs, team?._id, this, this
            )
            binding.tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
            binding.tabLayout.isInlineLabel = true

            TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position ->
                val title = (binding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
                tab.text = title
            }.attach()

            binding.viewPager2.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        val adapter = binding.viewPager2.adapter as? TeamPagerAdapter
                        val pageConfig = adapter?.getPageConfig(position) ?: pageConfigs.getOrNull(position)
                        val pageId = pageConfig?.id
                        team?._id?.let { teamId ->
                            pageId?.let {
                                teamLastPage[teamId] = it
                            }
                        }

                        val itemId = adapter?.getItemId(position) ?: position.toLong()
                        val fragmentTag = "f$itemId"
                        val fragment = childFragmentManager.findFragmentByTag(fragmentTag)
                        if (fragment is OnTeamPageListener) {
                            MainApplication.listener = fragment
                        }
                    }
                }
            )
        }

        binding.viewPager2.post {
            selectPage(restorePageId, false)
        }
    }

    private fun setupNonMyTeamButtons(user: RealmUser?, hasPendingRequest: Boolean) {
        binding.btnAddDoc.isEnabled = false
        binding.btnAddDoc.visibility = View.GONE
        binding.btnLeave.isEnabled = true
        binding.btnLeave.visibility = View.VISIBLE

        if (user?.id?.startsWith("guest") == true) {
            binding.btnLeave.isEnabled = false
            binding.btnLeave.visibility = View.GONE
        }

        val teamId = team?._id
        if (teamId.isNullOrEmpty()) {
            Utilities.toast(activity, getString(R.string.no_team_available))
            return
        }

        if (hasPendingRequest) {
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
                    teamsSyncRepository.syncTeamActivities()
                }
            }
        }
    }

    private fun setupMyTeamButtons(user: RealmUser?) {
        binding.btnAddDoc.isEnabled = true
        binding.btnAddDoc.visibility = View.VISIBLE
        binding.btnLeave.isEnabled = true
        binding.btnLeave.visibility = View.VISIBLE

        binding.btnLeave.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    team?.let { currentTeam ->
                        user?.let { currentUser ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val teamId = currentTeam._id ?: return@launch
                                teamsRepository.leaveTeam(teamId, currentUser.id)
                                Utilities.toast(activity, getString(R.string.left_team))
                                val lastPageId =
                                    currentTeam._id?.let { teamLastPage[it] } ?: arguments?.getString("navigateToPage")
                                setupViewPager(false, lastPageId)
                                binding.llActionButtons.visibility = View.GONE
                            }
                        }
                    }
                }.setNegativeButton(R.string.no, null).show()
        }

        binding.btnAddDoc.setOnClickListener {
            MainApplication.showDownload = true
            val isEnterprise = team?.type == "enterprise"
            val targetPageId = if (isEnterprise) DocumentsPage.id else CoursesPage.id
            val targetPageIndex = pageIndexById(targetPageId)
            val isAlreadyOnTargetPage = targetPageIndex != null && binding.viewPager2.currentItem == targetPageIndex

            selectPage(targetPageId)
            MainApplication.showDownload = false

            val delayMs = if (isAlreadyOnTargetPage) 50L else 300L

            viewLifecycleOwner.lifecycleScope.launch {
                delay(delayMs)
                val pageListener = childFragmentManager.fragments.firstOrNull {
                    it is OnTeamPageListener && it.arguments?.getString("fragmentType") == targetPageId
                } as? OnTeamPageListener
                when {
                    pageListener != null -> {
                        if (isEnterprise) {
                            pageListener.onAddDocument()
                        } else {
                            pageListener.onAddCourse()
                        }
                    }
                    MainApplication.listener is OnTeamPageListener -> {
                        if (isEnterprise) {
                            MainApplication.listener?.onAddDocument()
                        } else {
                            MainApplication.listener?.onAddCourse()
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshTeamDetails() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            val primaryTeamId = requireArguments().getString("id") ?: ""
            val fallbackTeamId = directTeamId ?: ""
            val isMyTeam = requireArguments().getBoolean("isMyTeam", false)

            val updatedTeam = when {
                primaryTeamId.isNotEmpty() -> teamsRepository.getTeamByIdOrTeamId(primaryTeamId)
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
                    val memberCount = teamsRepository.getJoinedMemberCount(id)
                    if (memberCount <= 1 && isMyTeam) {
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
            viewLifecycleOwner.lifecycleScope.launch {
                val joinedCount = teamsRepository.getJoinedMemberCount(teamId)
                binding.btnLeave.visibility = if (joinedCount <= 1) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
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
        viewLifecycleOwner.lifecycleScope.launch {
            val userModel = userSessionManager.getUserModel() ?: return@launch
            val userName = userModel.name
            val userPlanetCode = userModel.planetCode
            val userParentCode = userModel.parentCode
            val teamType = getEffectiveTeamType()
            teamsRepository.logTeamVisit(
                teamId = getEffectiveTeamId(),
                userName = userName,
                userPlanetCode = userPlanetCode,
                userParentCode = userParentCode,
                teamType = teamType,
            )
        }
    }

    private fun setupRealtimeSync() {
        collectWhenStarted(syncManagerInstance.dataUpdateFlow) { update ->
            if (update.table == "teams" && update.shouldRefreshUI) {
                refreshTeamDetails()
            }
        }
    }

    private fun shouldQueryRealm(teamId: String): Boolean {
        return teamId.isNotEmpty()
    }

    override fun onDestroyView() {
        loadTeamJob?.cancel()
        loadTeamJob = null
        super.onDestroyView()
        _binding = null
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
