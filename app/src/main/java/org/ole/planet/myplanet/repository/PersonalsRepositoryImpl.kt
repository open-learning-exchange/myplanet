package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.ole.planet.myplanet.data.room.dao.PersonalDao
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.GsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.distinctByContent

class PersonalsRepositoryImpl @Inject constructor(
    private val personalDao: PersonalDao,
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

    override fun getPersonalResources(userId: String?): Flow<List<Personal>> {
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

    internal suspend fun uploadPersonalDocument(personal: Personal): Pair<String, String>? {
        val response = uploadRepository.postUpload(
            "${UrlUtils.getUrl()}/resources",
            serialize(personal)
        )

        val `object` = response.body()
        if (`object` != null) {
            val rev = getString("rev", `object`)
            val id = getString("id", `object`)
            personalDao.updateRemoteDocRef(personal.id, id, rev)
            return Pair(id, rev)
        }
        return null
    }

    private fun serialize(personal: Personal): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", personal.title)
        `object`.addProperty("uploadDate", System.currentTimeMillis())
        `object`.addProperty("createdDate", personal.date)
        `object`.addProperty("filename", FileUtils.getFileNameFromUrl(personal.path))
        `object`.addProperty("author", personal.userName)
        `object`.addProperty("addedBy", personal.userName)
        `object`.addProperty("description", personal.description)
        `object`.addProperty("resourceType", "Activities")
        `object`.addProperty("private", true)
        val object1 = JsonObject()
        `object`.addDocumentOrigin()
        `object`.addProperty("deviceName", deviceNameProvider.getDeviceName())
        `object`.addProperty("customDeviceName", deviceNameProvider.getCustomDeviceName())
        object1.addProperty("users", personal.userId)
        `object`.add("privateFor", object1)
        return `object`
    }

    override suspend fun uploadPersonal(personal: Personal): String {
        if (personal.isUploaded) {
            return "Resource already uploaded"
        }

        try {
            val existingId = personal._id
            val existingRev = personal._rev
            val (id, rev) = if (!existingId.isNullOrBlank() && !existingRev.isNullOrBlank()) {
                existingId to existingRev
            } else {
                val result = uploadPersonalDocument(personal)
                    ?: return "Failed to upload personal resource: No response"
                result
            }

            val path = personal.path
            if (path != null) {
                val file = File(path)
                val name = FileUtils.getFileNameFromUrl(path)

                try {
                    uploadRepository.uploadAttachment(
                        file = file,
                        destinationFormat = "%s/resources/%s/%s",
                        id = id,
                        rev = rev,
                        name = name
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    return "Uploaded document but failed to upload attachment: ${e.message}"
                }
            }

            updatePersonalAfterSync(personal.id, id, rev)
            return "Personal resource uploaded successfully"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Unable to upload resource: ${e.message}"
        }
    }
}
