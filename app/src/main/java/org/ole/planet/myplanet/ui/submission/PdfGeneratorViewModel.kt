package org.ole.planet.myplanet.ui.submission

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.SubmissionPdfGenerator
import java.io.File
import javax.inject.Inject

class PdfGeneratorViewModel @Inject constructor(private val databaseService: DatabaseService) :
    ViewModel() {

    private val _pdfGenerationState = MutableLiveData<PdfGenerationState>()
    val pdfGenerationState: LiveData<PdfGenerationState> = _pdfGenerationState

    private var generationJob: Job? = null

    fun generatePdf(context: Context, submission: RealmSubmission) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _pdfGenerationState.value = PdfGenerationState.Loading
            try {
                val file = SubmissionPdfGenerator.generateSubmissionPdfAsync(context, submission, databaseService)
                _pdfGenerationState.value = file?.let { PdfGenerationState.Success(it) }
                    ?: PdfGenerationState.Error("Failed to generate PDF")
            } catch (e: Exception) {
                _pdfGenerationState.value = PdfGenerationState.Error("Failed to generate PDF: ${e.message}")
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
        _pdfGenerationState.value = PdfGenerationState.Idle
    }
}

sealed class PdfGenerationState {
    object Idle : PdfGenerationState()
    object Loading : PdfGenerationState()
    data class Success(val file: File) : PdfGenerationState()
    data class Error(val message: String) : PdfGenerationState()
}
