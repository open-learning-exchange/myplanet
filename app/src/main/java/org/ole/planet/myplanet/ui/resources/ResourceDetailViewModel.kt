package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class ResourceDetailViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val userRepository: UserRepository,
    private val ratingsRepository: RatingsRepository
) : ViewModel() {

    private val _library = MutableStateFlow<MyLibrary?>(null)
    val library: StateFlow<MyLibrary?> = _library.asStateFlow()

    private val _userModel = MutableStateFlow<UserEntity?>(null)
    val userModel: StateFlow<UserEntity?> = _userModel.asStateFlow()

    private val _rating = MutableStateFlow<JsonObject?>(null)
    val rating: StateFlow<JsonObject?> = _rating.asStateFlow()

    private val _isLibraryNotFound = MutableStateFlow(false)
    val isLibraryNotFound: StateFlow<Boolean> = _isLibraryNotFound.asStateFlow()

    fun initData(libraryId: String?) {
        if (_library.value != null) return

        viewModelScope.launch {
            _userModel.value = userRepository.getUserModel()

            if (libraryId.isNullOrBlank()) {
                _isLibraryNotFound.value = true
                return@launch
            }

            val fetchedLibrary = resourcesRepository.getLibraryItemById(libraryId)
                ?: resourcesRepository.getLibraryItemByResourceId(libraryId)

            if (fetchedLibrary == null) {
                _isLibraryNotFound.value = true
                return@launch
            }

            _library.value = fetchedLibrary
            loadRating(fetchedLibrary.resourceId)
        }
    }

    fun onDownloadComplete(libraryId: String?) {
        if (libraryId == null) return

        viewModelScope.launch {
            val userId = _userModel.value?.id
            try {
                val backgroundLibrary = resourcesRepository.getLibraryItemById(libraryId)
                    ?: resourcesRepository.getLibraryItemByResourceId(libraryId)

                val updatedLibrary = when {
                    backgroundLibrary == null -> null
                    backgroundLibrary.userId?.contains(userId) != true && userId != null ->
                        resourcesRepository.updateUserLibrary(libraryId, userId, true)
                    else -> backgroundLibrary
                }

                if (updatedLibrary != null) {
                    _library.value = updatedLibrary
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleLibraryAdd(libraryId: String?, isAdd: Boolean) {
        if (libraryId == null) return

        viewModelScope.launch {
            val userId = _userModel.value?.id ?: return@launch

            try {
                val updatedLibrary = resourcesRepository.updateUserLibrary(libraryId, userId, isAdd)
                if (updatedLibrary != null) {
                    _library.value = updatedLibrary
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadRating(resourceId: String?) {
        viewModelScope.launch {
            try {
                val userId = _userModel.value?.id
                _rating.value = ratingsRepository.getRatingsById("resource", resourceId, userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
