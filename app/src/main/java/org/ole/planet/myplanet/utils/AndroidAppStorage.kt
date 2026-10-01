package org.ole.planet.myplanet.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppStorage {
    override fun externalFilesDirPath(): String? = FileUtils.getExternalFilesDir(context)?.path

    override fun olePath(): String = FileUtils.getOlePath(context)

    override fun hasDownloadedFile(url: String?): Boolean = FileUtils.checkFileExist(context, url)
}
