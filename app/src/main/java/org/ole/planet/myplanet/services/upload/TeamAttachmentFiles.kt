package org.ole.planet.myplanet.services.upload

import android.content.Context
import java.io.File
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.FileUtils

/**
 * Transitional Context overload for [TeamsUploader], which still resolves team attachments from a
 * Context. [MyTeam.getAttachmentFile] now takes the ole path so the model stays platform-free;
 * delete this once TeamsUploader reads the path from AppStorage.
 */
internal fun MyTeam.Companion.getAttachmentFile(context: Context, teamId: String?, imageName: String?): File? =
    MyTeam.getAttachmentFile(FileUtils.getOlePath(context), teamId, imageName)
