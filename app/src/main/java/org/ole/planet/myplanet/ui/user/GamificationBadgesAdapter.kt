package org.ole.planet.myplanet.ui.user

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemGamificationBadgeBinding
import org.ole.planet.myplanet.model.gamification.GamificationBadge
import org.ole.planet.myplanet.utils.DiffUtils

class GamificationBadgesAdapter : ListAdapter<GamificationBadge, GamificationBadgesAdapter.BadgeViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val binding = ItemGamificationBadgeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BadgeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BadgeViewHolder(private val binding: ItemGamificationBadgeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(badge: GamificationBadge) {
            val context = binding.root.context
            binding.tvBadgeEmoji.text = badge.iconEmoji
            binding.tvBadgeTitle.text = badge.title
            binding.tvBadgeDescription.text = badge.description

            if (badge.isUnlocked) {
                binding.flBadgeIcon.setBackgroundResource(R.drawable.bg_badge_unlocked_circle)
                binding.ivBadgeCheck.visibility = View.VISIBLE
                binding.tvBadgeStatus.text = context.getString(R.string.unlocked_badge)
                binding.tvBadgeStatus.setTextColor(ContextCompat.getColor(context, R.color.status_completed))
                binding.pbBadgeProgress.progress = 100
                binding.cardBadge.alpha = 1.0f
            } else {
                binding.flBadgeIcon.setBackgroundResource(R.drawable.bg_badge_locked_circle)
                binding.ivBadgeCheck.visibility = View.GONE
                binding.tvBadgeStatus.text = context.getString(
                    R.string.in_progress_badge,
                    badge.currentProgress.coerceAtMost(badge.maxProgress),
                    badge.maxProgress
                )
                binding.tvBadgeStatus.setTextColor(ContextCompat.getColor(context, R.color.hint_color))
                binding.pbBadgeProgress.progress = badge.progressPercentage
                binding.cardBadge.alpha = 0.85f
            }
        }
    }
}
