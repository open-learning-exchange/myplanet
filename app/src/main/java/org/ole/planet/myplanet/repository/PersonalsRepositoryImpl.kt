package org.ole.planet.myplanet.repository

import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.PersonalDao
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.distinctByContent

class PersonalsRepositoryImpl @Inject constructor(
    private val personalDao: PersonalDao,
    private val apiInterface: ApiInterface,
    private val uploadRepository: UploadRepository,
    private val deviceNameProvider: DeviceNameProvider
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
        return personalDao.getByUserIdFlow(userId).distinctByContent { a, b ->
            // Compare CouchDB sync markers alongside fields editable locally via updatePersonalResource
            a.id == b.id && a._rev == b._rev && a.isUploaded == b.isUploaded &&
                a.title == b.title && a.description == b.description && a.path == b.path
        }
    }

    override suspend fun deletePersonalResource(id: String) {
        personalDao.deleteByIdOrDocId(id)
    }

    override suspend fun updatePersonalResource(id: String, update: PersonalUpdate) {
        personalDao.updateFields(id, update.title, update.description)
    }

    override suspend fun getPendingPersonalUploads(userId: String): List<Personal> {
        return personalDao.getPendingUploads(userId)
    }

    override suspend fun updatePersonalAfterSync(id: String, newId: String, rev: String) {
        personalDao.updateUploadedStatus(id, newId, rev)
    }

    override suspend fun uploadPersonalDocument(personal: Personal): Pair<String, String>? {
        val response = apiInterface.postDoc(
            UrlUtils.header, "application/json",
            "${UrlUtils.getUrl()}/resources", Personal.serialize(personal, deviceNameProvider.getCustomDeviceName())
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

    override suspend fun uploadPersonal(personal: Personal): String {
        if (personal.isUploaded) {
            return "Resource already uploaded"
        }

        try {
            val result = uploadPersonalDocument(personal)
            if (result != null) {
                val (id, rev) = result

                val path = personal.path
                if (path != null) {
                    val file = File(path)
                    val name = FileUtils.getFileNameFromUrl(path)

                    try {
                        val response = uploadRepository.uploadAttachment(
                            file = file,
                            destinationFormat = "%s/resources/%s/%s",
                            id = id,
                            rev = rev,
                            name = name
                        )
                        // Note: ignoring specific response success check to match old behavior
                        // which relied on callback but didn't block returning success
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Attachment upload failed but document succeeded
                    }
                }

                return "Personal resource uploaded successfully"
            } else {
                return "Failed to upload personal resource: No response"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "Unable to upload resource: ${e.message}"
        }
    }
}
