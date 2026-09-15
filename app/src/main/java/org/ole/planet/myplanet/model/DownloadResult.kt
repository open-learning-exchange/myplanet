package org.ole.planet.myplanet.model

import okhttp3.ResponseBody

sealed class DownloadResult {
    data class Success(val body: ResponseBody, val code: Int) : DownloadResult()
    data class Error(val message: String, val code: Int? = null) : DownloadResult()
}
