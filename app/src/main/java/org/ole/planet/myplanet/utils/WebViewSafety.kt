package org.ole.planet.myplanet.utils

import androidx.core.net.toUri

object WebViewSafety {

    fun isUrlSafe(
        url: String,
        trustedHosts: List<String>,
        resourceId: String?,
        appDir: String
    ): Boolean {
        return try {
            val uri = url.toUri()
            when {
                // Allow HTTPS URLs
                uri.scheme == "https" -> true

                // Allow HTTP URLs only for trusted Planet servers
                uri.scheme == "http" -> isTrustedPlanetServer(uri.host, trustedHosts)

                // Allow file URLs only for local resources and only from app's directory
                uri.scheme == "file" -> {
                    if (resourceId != null && appDir.isNotEmpty()) {
                        url.startsWith("file://$appDir")
                    } else {
                        false
                    }
                }
                // Block everything else
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isTrustedPlanetServer(host: String?, trustedHosts: List<String>): Boolean {
        if (host == null) return false

        return trustedHosts.any { url ->
            host == url || host.endsWith(".$url")
        }
    }
}
