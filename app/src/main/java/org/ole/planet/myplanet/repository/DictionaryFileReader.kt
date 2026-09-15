package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.FileUtils

interface DictionaryFileReader {
    fun exists(): Boolean
    fun readText(): String?
}

class DictionaryFileReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DictionaryFileReader {

    override fun exists(): Boolean {
        return FileUtils.checkFileExist(context, Constants.DICTIONARY_URL)
    }

    override fun readText(): String? {
        val path = FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL)
        return FileUtils.getStringFromFile(path)
    }
}
