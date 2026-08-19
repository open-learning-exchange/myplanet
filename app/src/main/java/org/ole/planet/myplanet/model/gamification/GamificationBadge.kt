package org.ole.planet.myplanet.model.gamification

data class GamificationBadge(
    val id: String,
    val title: String,
    val description: String,
    val category: BadgeCategory,
    val iconEmoji: String,
    val currentProgress: Int,
    val maxProgress: Int,
    val isUnlocked: Boolean,
    val unlockedDate: Long? = null
) {
    val progressPercentage: Int
        get() = if (maxProgress > 0) {
            ((currentProgress.coerceAtMost(maxProgress).toDouble() / maxProgress) * 100).toInt()
        } else 0
}
