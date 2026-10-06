package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.data.api.StreamBody

sealed class DownloadResult {
    data class Success(val body: StreamBody, val code: Int, val validator: String? = null) : DownloadResult()
    data class Error(val message: String, val code: Int? = null) : DownloadResult()
}
