package org.ole.planet.myplanet.ui.community.leaders.models

import org.ole.planet.myplanet.domain.models.Leader

data class LeadersUIState(
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val leaders: List<Leader> = emptyList()
)
