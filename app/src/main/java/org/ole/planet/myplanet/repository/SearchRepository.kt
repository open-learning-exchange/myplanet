package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmTag

interface SearchRepository {
    suspend fun saveSearchActivity(
        userId: String?,
        userPlanetCode: String?,
        userParentCode: String?,
        searchText: String,
        tags: List<RealmTag>,
        gradeLevel: String,
        subjectLevel: String
    )
}
