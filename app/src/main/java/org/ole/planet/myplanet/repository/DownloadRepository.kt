package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.DownloadResult

interface DownloadRepository {
    suspend fun downloadFileResponse(url: String, authHeader: String): DownloadResult
}
