package org.ole.planet.myplanet.utilities

import java.util.regex.Pattern

object MarkdownImageUtils {
    fun prependBaseUrlToImages(markdownContent: String?, baseUrl: String, width: Int = 150, height: Int = 100): String {
        val pattern = "!\\[.*?]\\((.*?)\\)"
        val imagePattern = Pattern.compile(pattern)
        val matcher = markdownContent?.let { imagePattern.matcher(it) } ?: return markdownContent.orEmpty()
        val result = StringBuffer()
        while (matcher.find()) {
            val relativePath = matcher.group(1)
            val modifiedPath = relativePath?.replaceFirst("resources/".toRegex(), "")
            val fullUrl = baseUrl + modifiedPath
            matcher.appendReplacement(result, "<img src=$fullUrl width=$width height=$height/>")
        }
        matcher.appendTail(result)
        return result.toString()
    }
}
