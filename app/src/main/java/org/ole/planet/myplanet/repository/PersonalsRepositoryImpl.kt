package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.PersonalDao
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils

class PersonalsRepositoryImpl @Inject constructor(
    private val personalDao: PersonalDao,
    private val apiInterface: ApiInterface,
    @ApplicationContext private val context: Context
) : PersonalsRepository {

    override suspend fun personalTitleExists(title: String, userId: String?): Boolean {
        return personalDao.countByTitle(title, userId) > 0
    }

    override suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userName: String?,
        path: String?,
        description: String?
    ) {
        val personal = Personal().apply {
            id = UUID.randomUUID().toString()
            _id = id
            this.title = title
            this.userId = userId
            this.userName = userName
            this.path = path
            this.date = Date().time
            this.description = description
        }
        personalDao.insert(personal)
    }

    override suspend fun getPersonalResources(userId: String?): Flow<List<Personal>> {
        if (userId.isNullOrBlank()) {
            return flowOf(emptyList())
        }
        return personalDao.getByUserIdFlow(userId)
    }

    override suspend fun deletePersonalResource(id: String) {
        personalDao.deleteByDocId(id)
        personalDao.deleteById(id)
    }

    override suspend fun updatePersonalResource(id: String, updater: (Personal) -> Unit) {
        personalDao.findByDocId(id)?.let { personal ->
            updater(personal)
            personalDao.update(personal)
        }
        personalDao.findById(id)?.let { personal ->
            updater(personal)
            personalDao.update(personal)
        }
    }

    override suspend fun getPendingPersonalUploads(userId: String): List<Personal> {
        return personalDao.getPendingUploads(userId)
    }

    override suspend fun updatePersonalAfterSync(id: String, newId: String, rev: String) {
        personalDao.findById(id)?.let { personal ->
            personal.isUploaded = true
            personal._id = newId
            personal._rev = rev
            personalDao.update(personal)
        }
    }

    override suspend fun uploadPersonalDocument(personal: Personal): Pair<String, String>? {
        val response = apiInterface.postDoc(
            UrlUtils.header, "application/json",
            "${UrlUtils.getUrl()}/resources", Personal.serialize(personal, context)
        )

        val `object` = response.body()
        if (`object` != null) {
            val rev = getString("rev", `object`)
            val id = getString("id", `object`)

            personal.id.let { personalId ->
                updatePersonalAfterSync(personalId, id, rev)
            }

            return Pair(id, rev)
        }
        return null
    }
}
