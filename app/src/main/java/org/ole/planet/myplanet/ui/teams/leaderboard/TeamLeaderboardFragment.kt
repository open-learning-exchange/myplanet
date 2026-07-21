package org.ole.planet.myplanet.ui.teams.leaderboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.databinding.FragmentTeamLeaderboardBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.TeamLeaderboardEntry
import org.ole.planet.myplanet.repository.JoinedMemberData
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.utils.TimeProvider

@AndroidEntryPoint
class TeamLeaderboardFragment : BaseTeamFragment() {
    @Inject
    lateinit var progressRepository: ProgressRepository
    @Inject
    lateinit var timeProvider: TimeProvider

    private var _binding: FragmentTeamLeaderboardBinding? = null
    private val binding get() = _binding!!
    private var adapter: TeamLeaderboardAdapter? = null

    private enum class Period { ALL_TIME, THIS_MONTH }
    private var period = Period.ALL_TIME

    private var courseIds: List<String> = emptyList()
    private var members: List<JoinedMemberData> = emptyList()
    private var progressByUser: Map<String, Map<String?, JsonObject>> = emptyMap()
    private var surveyTimestampsByUser: Map<String, List<Long>> = emptyMap()
    private var surveysTotal: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TeamLeaderboardAdapter()
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(activity)
        binding.rvLeaderboard.adapter = adapter
        binding.btnPeriodAllTime.setOnClickListener { selectPeriod(Period.ALL_TIME) }
        binding.btnPeriodThisMonth.setOnClickListener { selectPeriod(Period.THIS_MONTH) }
        loadLeaderboard()
    }

    private fun selectPeriod(newPeriod: Period) {
        if (period == newPeriod) return
        period = newPeriod
        adapter?.collapseAll()
        render()
    }

    private fun loadLeaderboard() {
        viewLifecycleOwner.lifecycleScope.launch {
            courseIds = teamsRepository.getTeamCourseIds(teamId)
            members = teamsRepository.getJoinedMembersWithVisitInfo(teamId)
            val progressMap = mutableMapOf<String, Map<String?, JsonObject>>()
            for (member in members) {
                val userId = member.user.id ?: continue
                progressMap[userId] = progressRepository.getCourseProgress(courseIds, userId)
            }
            progressByUser = progressMap
            surveysTotal = surveysRepository.getTeamOwnedSurveys(teamId).size
            surveyTimestampsByUser = surveysRepository.getTeamSurveyCompletionTimestamps(teamId)
            if (isAdded) {
                render()
            }
        }
    }

    private fun render() {
        val periodStart = if (period == Period.THIS_MONTH) {
            TeamLeaderboardCalculator.startOfCurrentMonth(timeProvider.now())
        } else {
            null
        }
        val entries = TeamLeaderboardCalculator.build(
            members = members,
            courseIds = courseIds,
            progressByUser = progressByUser,
            surveyCompletionTimestampsByUser = surveyTimestampsByUser,
            surveysTotal = surveysTotal,
            currentUserId = sharedPrefManager.getUserId(),
            periodStart = periodStart
        )
        adapter?.submitList(entries)
        showNoData(binding.tvNodata, entries.size, "teamLeaderboard")
        updatePeriodChips()
        updateFilterCaption()
        updateSummaryBanner(entries)
    }

    private fun updatePeriodChips() {
        val context = requireContext()
        val isAllTime = period == Period.ALL_TIME
        binding.btnPeriodAllTime.setBackgroundResource(
            if (isAllTime) R.drawable.bg_period_chip_active else R.drawable.bg_period_chip_inactive
        )
        binding.btnPeriodAllTime.setTextColor(
            ContextCompat.getColor(context, if (isAllTime) R.color.colorPrimaryDark else R.color.leaderboard_ink_2)
        )
        binding.btnPeriodThisMonth.setBackgroundResource(
            if (!isAllTime) R.drawable.bg_period_chip_active else R.drawable.bg_period_chip_inactive
        )
        binding.btnPeriodThisMonth.setTextColor(
            ContextCompat.getColor(context, if (!isAllTime) R.color.colorPrimaryDark else R.color.leaderboard_ink_2)
        )
    }

    private fun updateFilterCaption() {
        binding.tvFilterCaption.text = getString(R.string.leaderboard_filter_caption, courseIds.size, surveysTotal)
    }

    private fun updateSummaryBanner(entries: List<TeamLeaderboardEntry>) {
        val teamDone = entries.sumOf { it.coursesCompleted + it.surveysCompleted }
        val totalPossible = (courseIds.size + surveysTotal) * entries.size
        binding.tvTeamProgress.text = getString(R.string.leaderboard_team_progress_value, teamDone, totalPossible)
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {}

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
