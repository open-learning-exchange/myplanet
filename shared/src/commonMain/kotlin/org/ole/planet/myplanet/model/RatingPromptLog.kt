package org.ole.planet.myplanet.model

import androidx.room.Entity
import kotlin.time.Clock

@Entity(
    tableName = "rating_prompt_log",
    primaryKeys = ["userId", "item", "type"]
)
data class RatingPromptLog(
    val userId: String,
    val item: String,
    val type: String = "resource",
    val promptedAt: Long = Clock.System.now().toEpochMilliseconds()
)
