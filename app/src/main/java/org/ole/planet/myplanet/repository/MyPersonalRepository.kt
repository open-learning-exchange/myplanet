package org.ole.planet.myplanet.repository

interface MyPersonalRepository {
    suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userName: String?,
        path: String?,
        description: String?
    )
}
