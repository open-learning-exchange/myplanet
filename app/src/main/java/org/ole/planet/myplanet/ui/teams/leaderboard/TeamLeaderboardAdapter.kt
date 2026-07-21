package org.ole.planet.myplanet.ui.teams.leaderboard

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowLeaderboardMemberBinding
import org.ole.planet.myplanet.model.TeamLeaderboardEntry
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.teams.members.MembersDetailFragment
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.TimeUtils

class TeamLeaderboardAdapter : ListAdapter<TeamLeaderboardEntry, TeamLeaderboardAdapter.LeaderboardViewHolder>(DIFF_CALLBACK) {
    private var expandedUserId: String? = null

    fun collapseAll() {
        val previouslyExpanded = expandedUserId
        expandedUserId = null
        val index = currentList.indexOfFirst { it.userId == previouslyExpanded }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = RowLeaderboardMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val entry = getItem(position)
        val binding = holder.binding
        val context = binding.root.context
        val rank = position + 1
        val isExpanded = entry.userId != null && entry.userId == expandedUserId

        binding.tvRank.text = rank.toString()
        val (medalBg, medalFg) = medalColors(context, rank)
        (binding.tvRank.background.mutate() as GradientDrawable).setColor(medalBg)
        binding.tvRank.setTextColor(medalFg)

        binding.tvTitle.text = entry.displayName
        binding.tvYouPill.visibility = if (entry.isCurrentUser) View.VISIBLE else View.GONE

        val cardBg = if (entry.isCurrentUser) R.color.leaderboard_blue_50 else R.color.card_bg
        val cardStroke = if (entry.isCurrentUser) R.color.colorPrimaryLight else R.color.leaderboard_border
        binding.root.setCardBackgroundColor(ContextCompat.getColor(context, cardBg))
        binding.root.strokeColor = ContextCompat.getColor(context, cardStroke)

        binding.progressCourses.progress = percentOf(entry.coursesCompleted, entry.coursesTotal)
        binding.tvCoursesCompleted.text = context.getString(
            R.string.leaderboard_course_count, entry.coursesCompleted, entry.coursesTotal
        )
        binding.progressSurveys.progress = percentOf(entry.surveysCompleted, entry.surveysTotal)
        binding.tvSurveysCompleted.text = context.getString(
            R.string.leaderboard_survey_count, entry.surveysCompleted, entry.surveysTotal
        )

        binding.ivChevron.rotation = if (isExpanded) 180f else 0f
        binding.expandPanel.visibility = if (isExpanded) View.VISIBLE else View.GONE

        binding.tvCoursesDetail.text = context.getString(
            R.string.leaderboard_course_progress_of, entry.coursesCompleted, entry.coursesTotal
        )
        binding.tvSurveysDetail.text = context.getString(
            R.string.leaderboard_course_progress_of, entry.surveysCompleted, entry.surveysTotal
        )
        val lastVisitDate = entry.visitInfo.lastVisitDate
        binding.tvLastActive.text = if (lastVisitDate != null) {
            TimeUtils.getRelativeTime(lastVisitDate)
        } else {
            context.getString(R.string.no_visit)
        }

        val avatarSize = context.resources.getDimensionPixelSize(R.dimen._40dp)
        ImageUtils.loadProfileImage(entry.userImage, binding.memberImage, avatarSize)

        binding.rowCollapsed.setOnClickListener {
            val previouslyExpanded = expandedUserId
            expandedUserId = if (isExpanded) null else entry.userId
            notifyItemChanged(position)
            val previousIndex = currentList.indexOfFirst { it.userId == previouslyExpanded }
            if (previousIndex >= 0 && previousIndex != position) notifyItemChanged(previousIndex)
        }

        binding.btnViewMember.setOnClickListener {
            val activity = it.context as? AppCompatActivity ?: return@setOnClickListener
            val user = entry.visitInfo.user
            val userName = "${user.firstName} ${user.lastName}".trim().ifBlank { user.name.orEmpty() }
            val fragment = MembersDetailFragment.newInstance(
                userName,
                user.email.toString(),
                user.dob.toString().substringBefore("T"),
                user.language.toString(),
                user.phoneNumber.toString(),
                "${entry.visitInfo.visitCount}",
                entry.visitInfo.profileLastVisit,
                "${user.firstName} ${user.lastName}",
                user.level.toString(),
                user.userImage
            )
            FragmentNavigator.replaceFragment(
                activity.supportFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
    }

    private fun percentOf(completed: Int, total: Int): Int {
        if (total <= 0) return 0
        return (completed * 100 / total).coerceIn(0, 100)
    }

    private fun medalColors(context: android.content.Context, rank: Int): Pair<Int, Int> {
        val (bgRes, fgRes) = when (rank) {
            1 -> R.color.medal_gold_bg to R.color.medal_gold_fg
            2 -> R.color.medal_silver_bg to R.color.medal_silver_fg
            3 -> R.color.medal_bronze_bg to R.color.medal_bronze_fg
            else -> R.color.leaderboard_surface_alt to R.color.leaderboard_ink_2
        }
        return ContextCompat.getColor(context, bgRes) to ContextCompat.getColor(context, fgRes)
    }

    class LeaderboardViewHolder(val binding: RowLeaderboardMemberBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<TeamLeaderboardEntry>(
            areItemsTheSame = { old, new -> old.userId == new.userId },
            areContentsTheSame = { old, new -> old == new }
        )
    }
}
