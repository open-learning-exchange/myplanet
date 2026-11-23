package org.ole.planet.myplanet.services

import android.util.Log
import androidx.core.net.toUri
import javax.inject.Inject
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.UrlUtils

class FallbackUrlResolver @Inject constructor(
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager
) {
    data class Fallback(val primaryBase: String, val alternativeUrl: String)

    fun resolve(url: String): Fallback? {
        val mapping = serverUrlMapper.processUrl(url)
        val primaryBase = mapping.extractedBaseUrl
        if (primaryBase == null) {
            Log.d(TAG, "resolve: no base URL in ${UrlUtils.redactForLog(url)}")
            return null
        }
        val altBase = mapping.alternativeUrl
            ?: sharedPrefManager.getProcessedAlternativeUrl().takeIf { it.isNotEmpty() }?.trimEnd('/')
        if (altBase == null) {
            Log.d(TAG, "resolve: no alternative URL for primary base '$primaryBase'")
            return null
        }
        val parsed = url.toUri()
        val query = parsed.query?.let { "?$it" }.orEmpty()
        val alternativeUrl = DownloadService.alternativeDownloadUrl(altBase, parsed.path.orEmpty(), query)
        Log.d(TAG, "resolve: primary=$primaryBase, alternative=${UrlUtils.redactForLog(altBase)}")
        return Fallback(primaryBase, alternativeUrl)
    }

    companion object {
        private const val TAG = "FallbackUrlResolver"
    }
}
