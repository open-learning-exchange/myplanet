package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.GsonUtils

// App-side sync helpers for MyCourse (concatenated download links persisted to prefs), kept out
// of the Room entity.

private val concatenatedLinks = HashSet<String>()

fun MyCourse.Companion.addConcatenatedLink(link: String) {
    synchronized(concatenatedLinks) {
        concatenatedLinks.add(link)
    }
}

fun MyCourse.Companion.saveConcatenatedLinksToPrefs(spm: SharedPrefManager) {
    val existingJsonLinks = spm.getConcatenatedLinks()
    val existingConcatenatedLinks = if (existingJsonLinks != null) {
        GsonUtils.gson.fromJson(existingJsonLinks, Array<String>::class.java).toHashSet()
    } else {
        hashSetOf()
    }
    val linksToProcess: List<String>
    synchronized(concatenatedLinks) {
        linksToProcess = concatenatedLinks.toList()
    }
    existingConcatenatedLinks.addAll(linksToProcess)
    val jsonConcatenatedLinks = GsonUtils.gson.toJson(existingConcatenatedLinks)
    spm.setConcatenatedLinks(jsonConcatenatedLinks)
}
