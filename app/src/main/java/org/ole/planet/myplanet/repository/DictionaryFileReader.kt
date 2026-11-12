package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.StoragePathResolver

interface DictionaryFileReader {
    fun exists(): Boolean
    fun readText(): String?
}

class DictionaryFileReaderImpl @Inject constructor(
    private val storagePathResolver: StoragePathResolver
) : DictionaryFileReader {

    override fun exists(): Boolean {
        val file = storagePathResolver.resolveFileFromUrl(Constants.DICTIONARY_URL)
        return file.exists() && file.length() > 0
    }

    override fun readText(): String? {
        val file = storagePathResolver.resolveFileFromUrl(Constants.DICTIONARY_URL)
        if (!file.exists()) return null
        return FileUtils.getStringFromFile(file)
    }
}
