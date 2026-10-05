package org.ole.planet.myplanet.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils

data class StorageBreakdownUiState(
    val isLoading: Boolean = true,
    val availableSpaceText: String = "",
    val totalBytes: Long = 0L,
    val categories: List<StorageBreakdownViewModel.CategoryData> = emptyList()
)

@HiltViewModel
open class StorageBreakdownViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    data class CategoryData(
        @StringRes val nameRes: Int,
        val extensions: Set<String>,
        val sizeBytes: Long = 0,
        val fileCount: Int = 0
    )

    internal val categories: List<CategoryData> = StorageCategories.all.map {
        CategoryData(it.nameRes, it.extensions)
    }

    private val _uiState = MutableStateFlow(StorageBreakdownUiState())
    val uiState: StateFlow<StorageBreakdownUiState> = _uiState.asStateFlow()

    private var hasScanned = false

    init {
        loadStorage()
    }

    fun loadStorage(forceRefresh: Boolean = false) {
        if (hasScanned && !forceRefresh) return
        hasScanned = true

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(dispatcherProvider.io) {
            val availableSpaceText = FileUtils.availableOverTotalMemoryFormattedString(context)
            val result = resourcesRepository.getStorageBreakdown(File(FileUtils.getOlePath(context)))

            val scannedCategories = categories.mapIndexed { index, category ->
                category.copy(
                    sizeBytes = result.sizes[index],
                    fileCount = result.counts[index]
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    availableSpaceText = availableSpaceText,
                    totalBytes = result.totalBytes,
                    categories = scannedCategories
                )
            }
        }
    }
}
