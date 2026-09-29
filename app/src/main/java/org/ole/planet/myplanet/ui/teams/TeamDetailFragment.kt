package org.ole.planet.myplanet.ui.teams

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
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
import org.ole.planet.myplanet.callback.OnChangedListener
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.UserSessionManager
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
class TeamDetailFragment : BaseTeamFragment() {

    @Inject
    lateinit var userSessionManager: UserSessionManager

    private val teamViewModel: TeamViewModel by viewModels()

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

        viewLifecycleOwner.lifecycleScope.launch {
            val user = userSessionManager.getUserModel()
            teamViewModel.loadTeamDetail(
                primaryTeamId = teamId,
                fallbackTeamId = directTeamId,
                isMyTeam = isMyTeam,
                userId = user?.id
            )
        }

        return binding.root
    }

    private fun renderPlaceholder() {
        binding.title.text = directTeamName?.takeIf { it.isNotBlank() } ?: getString(R.string.loading_teams)
        binding.subtitle.text = directTeamType ?: ""
        binding.btnAddDoc.isEnabled = false
        binding.btnLeave.isEnabled = false
        binding.viewPager2.adapter = null
    }

    private fun setupTeamDetails(isMyTeam: Boolean, user: UserEntity?) {
        binding.title.text = getEffectiveTeamName()
        binding.subtitle.text = getEffectiveTeamType()

        if (!isMyTeam) {
            setupNonMyTeamButtons(user)
        } else {
            setupMyTeamButtons(user)
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
                this, pageConfigs, team?._id,
                OnChangedListener { onMemberChanged() },
                OnChangedListener { onTeamDetailsUpdated() }
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

    private fun setupNonMyTeamButtons(user: UserEntity?) {
        binding.btnAddDoc.isEnabled = false
        binding.btnAddDoc.visibility = View.GONE
        binding.btnLeave.isEnabled = true
        binding.btnLeave.visibility = View.VISIBLE

        if (user?.id?.startsWith("guest") == true) {
            binding.btnLeave.isEnabled = false
            binding.btnLeave.visibility = View.GONE
        }
    }

    private fun setupMyTeamButtons(user: UserEntity?) {
        binding.btnAddDoc.isEnabled = true
        binding.btnAddDoc.visibility = View.VISIBLE
        binding.btnLeave.isEnabled = true
        binding.btnLeave.visibility = View.VISIBLE

        binding.btnLeave.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    team?.let { currentTeam ->
                        user?.let { currentUser ->
                            val teamId = currentTeam._id ?: return@let
                            teamViewModel.leaveTeam(teamId, currentUser.id)
                            Utilities.toast(activity, getString(R.string.left_team))
                            val lastPageId =
                                currentTeam._id?.let { teamLastPage[it] } ?: arguments?.getString("navigateToPage")
                            setupViewPager(false, lastPageId)
                            binding.llActionButtons.visibility = View.GONE
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

    private fun refreshTeamDetails() {
        if (!isAdded || requireActivity().isFinishing) return

        val primaryTeamId = requireArguments().getString("id") ?: ""
        val fallbackTeamId = directTeamId ?: ""
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)

        viewLifecycleOwner.lifecycleScope.launch {
            val user = userSessionManager.getUserModel()
            teamViewModel.loadTeamDetail(primaryTeamId, fallbackTeamId, isMyTeam, user?.id)
        }
    }

    private fun onMemberChanged() {
        refreshTeamDetails()
    }

    private fun onTeamDetailsUpdated() {
        refreshTeamDetails()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setupRealtimeSync()
        createTeamLog()
    }

    private fun updateJoinButtonState(state: TeamJoinState, user: UserEntity?) {
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        if (isMyTeam) return

        if (user?.id?.startsWith("guest") == true) {
            binding.btnLeave.isEnabled = false
            binding.btnLeave.visibility = View.GONE
            return
        }

        val teamId = team?._id ?: teamViewModel.teamDetail.value?._id
        if (teamId.isNullOrEmpty()) return

        when (state) {
            TeamJoinState.PENDING -> {
                binding.btnLeave.text = getString(R.string.requested)
                binding.btnLeave.isEnabled = false
            }
            TeamJoinState.JOINABLE -> {
                binding.btnLeave.text = getString(R.string.join)
                binding.btnLeave.isEnabled = true
                binding.btnLeave.setOnClickListener {
                    val userId = user?.id
                    val userPlanetCode = user?.planetCode
                    val teamType = team?.teamType
                    teamViewModel.requestToJoin(teamId, userId, userPlanetCode, teamType)
                }
            }
            TeamJoinState.LEAVE -> {
                binding.btnLeave.text = getString(R.string.leave)
                binding.btnLeave.isEnabled = true
            }
        }
    }

    private fun observeViewModel() {
        collectWhenStarted(teamViewModel.teamDetail) { updatedTeam ->
            val primaryTeamId = requireArguments().getString("id") ?: ""
            if (shouldQueryRealm(primaryTeamId) && updatedTeam == null) {
                binding.loadingIndicator?.visibility = View.GONE
                Snackbar.make(
                    binding.root,
                    getString(R.string.no_team_available),
                    Snackbar.LENGTH_LONG
                ).show()
                return@collectWhenStarted
            }

            updatedTeam?.let {
                team = it
                directTeamName = it.name
                directTeamType = it.type
                requireArguments().apply {
                    putString("teamName", it.name)
                    putString("teamType", it.type)
                }

                binding.loadingIndicator?.visibility = View.GONE
                binding.contentLayout?.visibility = View.VISIBLE

                val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
                val user = userSessionManager.getUserModel()
                setupTeamDetails(isMyTeam, user)

                val lastPageId = it._id?.let { id -> teamLastPage[id] } ?: arguments?.getString("navigateToPage")
                setupViewPager(isMyTeam, lastPageId)

                updateJoinButtonState(teamViewModel.joinState.value, user)
            }
        }

        collectWhenStarted(teamViewModel.memberCount) { count ->
            val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
            if (isMyTeam) {
                if (count <= 1) {
                    binding.btnLeave.visibility = View.GONE
                } else {
                    binding.btnLeave.visibility = View.VISIBLE
                }
            }
        }

        collectWhenStarted(teamViewModel.joinState) { state ->
            val user = userSessionManager.getUserModel()
            updateJoinButtonState(state, user)
        }
    }

    override fun onNewsItemClick(news: News?) {}

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
            teamViewModel.logTeamVisit(
                teamId = getEffectiveTeamId(),
                userName = userName,
                userPlanetCode = userPlanetCode,
                userParentCode = userParentCode,
                teamType = teamType,
            )
        }
    }

    private fun setupRealtimeSync() {
        collectWhenStarted(
            teamViewModel.getTeamUpdateFlow()
        ) { update ->
            if (update.shouldRefreshUI) {
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
