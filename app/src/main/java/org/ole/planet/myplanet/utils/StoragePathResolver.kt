package org.ole.planet.myplanet.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class StoragePathResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveFileFromUrl(url: String?): File = FileUtils.getSDPathFromUrl(context, url)
    fun resolveOleDirectory(): File = File(FileUtils.getOlePath(context))

    fun resolveTeamAttachment(teamId: String?, imageName: String?): File? {
        if (teamId.isNullOrBlank() || imageName.isNullOrBlank()) return null
        return File("${FileUtils.getOlePath(context)}team_attachments/$teamId/$imageName")
    }
}
