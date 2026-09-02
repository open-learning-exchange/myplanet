package org.ole.planet.myplanet.ui.viewer

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils

@HiltViewModel
class ResourceViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourcesRepository: ResourcesRepository,
    private val authSessionUpdaterFactory: AuthSessionUpdater.Factory,
    private val configurationsRepository: ConfigurationsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    suspend fun ensureServerUrlUpdated() {
        configurationsRepository.ensureServerUrlUpdated()
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

    suspend fun getExternalFilesDir(): File? = withContext(dispatcherProvider.io) {
        context.getExternalFilesDir(null)
    }

    suspend fun downloadResource(url: String) = withContext(dispatcherProvider.io) {
        if (!FileUtils.checkFileExist(context, url)) {
            DownloadUtils.openDownloadService(context, arrayListOf(url), false)
        }
    }

    suspend fun extractPdfText(file: File): String = withContext(dispatcherProvider.io) {
        try {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(file).use {
                PDFTextStripper().getText(it).trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
