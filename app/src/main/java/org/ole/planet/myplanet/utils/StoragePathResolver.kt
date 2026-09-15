package org.ole.planet.myplanet.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import org.ole.planet.myplanet.model.MyTeam

class StoragePathResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveFileFromUrl(url: String?): File = FileUtils.getSDPathFromUrl(context, url)
    fun resolveOleDirectory(): File = File(FileUtils.getOlePath(context))

    fun resolveTeamAttachment(teamId: String?, imageName: String?): File? =
        MyTeam.getAttachmentFile(context, teamId, imageName)
}
