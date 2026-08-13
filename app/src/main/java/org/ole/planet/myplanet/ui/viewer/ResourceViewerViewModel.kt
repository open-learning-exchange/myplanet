package org.ole.planet.myplanet.ui.viewer

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import javax.inject.Inject
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class ResourceViewerViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val authSessionUpdaterFactory: AuthSessionUpdater.Factory,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    suspend fun ensureServerUrlUpdated() {
        val serverUrl = sharedPrefManager.getServerUrl()
        val mapping = serverUrlMapper.processUrl(serverUrl)
        if (mapping.alternativeUrl != null) {
            serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                serverUrlMapper.isUrlDirectlyReachable(url)
            }
        }
    }

    fun getAuthSessionUpdater(callback: AuthSessionUpdater.AuthCallback): AuthSessionUpdater {
        return authSessionUpdaterFactory.create(callback)
    }

    suspend fun getLibraryItemById(id: String): MyLibrary? {
        return resourcesRepository.getLibraryItemById(id)
    }

    suspend fun updateLibraryItemTranslationAudioPath(id: String, outputFile: String?) {
        resourcesRepository.updateLibraryItem(id) { it.translationAudioPath = outputFile }
    }

    suspend fun getExternalFilesDir(context: Context): File? = withContext(dispatcherProvider.io) {
        context.getExternalFilesDir(null)
    }

    suspend fun downloadResource(context: Context, url: String) = withContext(dispatcherProvider.io) {
        if (!FileUtils.checkFileExist(context, url)) {
            DownloadUtils.openDownloadService(context, arrayListOf(url), false)
        }
    }

    suspend fun extractPdfText(context: Context, file: File): String = withContext(dispatcherProvider.io) {
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
            val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(document).trim()
            document.close()
            text
        } catch (e: Exception) { "" }
    }
}
