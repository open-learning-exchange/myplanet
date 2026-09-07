package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Personal

data class PersonalUpdate(
    val title: String? = null,
    val description: String? = null
)

interface PersonalsRepository {
    suspend fun personalTitleExists(title: String, userId: String?): Boolean

    suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userName: String?,
        path: String?,
        description: String?
    )

    suspend fun getPersonalResources(userId: String?): Flow<List<Personal>>
    suspend fun deletePersonalResource(id: String)
    suspend fun updatePersonalResource(id: String, update: PersonalUpdate)
    suspend fun getPendingPersonalUploads(userId: String): List<Personal>
    suspend fun updatePersonalAfterSync(id: String, newId: String, rev: String)
    suspend fun uploadPersonalDocument(personal: Personal): Pair<String, String>?
    suspend fun uploadPersonal(personal: Personal): String
}
