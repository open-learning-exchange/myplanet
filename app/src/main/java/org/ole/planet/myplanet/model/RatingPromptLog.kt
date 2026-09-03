package org.ole.planet.myplanet.model

import androidx.room.Entity

@Entity(
    tableName = "rating_prompt",
    primaryKeys = ["userId", "item", "type"]
)
data class RatingPromptLog(
    val userId: String,
    val item: String,
    val type: String = "resource",
    val promptedAt: Long = System.currentTimeMillis()
)
