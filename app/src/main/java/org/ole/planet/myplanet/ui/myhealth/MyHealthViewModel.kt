package org.ole.planet.myplanet.ui.myhealth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.UserRepository
import javax.inject.Inject

sealed class SearchState {
    object Loading : SearchState()
    data class Success(val users: List<RealmUserModel>) : SearchState()
    object Error : SearchState()
    object Idle : SearchState()
}

@HiltViewModel
class MyHealthViewModel @Inject constructor(private val userRepository: UserRepository) : ViewModel() {

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState = _search-state.asStateFlow()

    private var searchJob: Job? = null

    fun searchUsers(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.value = SearchState.Loading
            delay(300) // Debounce time
            try {
                val users = userRepository.searchUsers(query)
                _searchState.value = SearchState.Success(users)
            } catch (e: Exception) {
                Log.e("MyHealthViewModel", "Search failed", e)
                _searchState.value = SearchState.Error
            }
        }
    }
}
