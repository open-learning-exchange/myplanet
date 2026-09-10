package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.FileUtils

interface DictionaryAssetDataSource {
    fun isDictionaryAssetPresent(): Boolean
    fun readDictionaryAssetText(): String?
}

class DictionaryAssetDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DictionaryAssetDataSource {

    override fun isDictionaryAssetPresent(): Boolean {
        return FileUtils.checkFileExist(context, Constants.DICTIONARY_URL)
    }

    override fun readDictionaryAssetText(): String? {
        val path = FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL)
        return FileUtils.getStringFromFile(path)
    }
}
