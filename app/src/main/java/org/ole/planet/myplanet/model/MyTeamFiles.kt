package org.ole.planet.myplanet.model

import android.content.Context
import java.io.File
import org.ole.planet.myplanet.utils.FileUtils

// App-side java.io.File helpers for MyTeam, kept out of the Room entity.

/** [olePath] is the `<external files>/ole/` directory, trailing slash included (see AppStorage.olePath). */
fun MyTeam.Companion.getAttachmentFile(olePath: String, teamId: String?, imageName: String?): File? {
    if (teamId.isNullOrBlank() || imageName.isNullOrBlank()) return null
    return File(
        "${olePath}team_attachments/$teamId/$imageName"
    )
}

/**
 * Transitional Context overload for TeamsUploader, which still resolves team attachments from a
 * Context; delete this once TeamsUploader reads the path from AppStorage.
 */
internal fun MyTeam.Companion.getAttachmentFile(context: Context, teamId: String?, imageName: String?): File? =
    getAttachmentFile(FileUtils.getOlePath(context), teamId, imageName)
