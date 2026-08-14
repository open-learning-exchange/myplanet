package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.repository.TagsRepository

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val tagsRepository: TagsRepository
) : ViewModel() {

    private val _tagsWithChildren = MutableStateFlow<Map<TagEntity, List<TagEntity>>?>(null)
    val tagsWithChildren: StateFlow<Map<TagEntity, List<TagEntity>>?> = _tagsWithChildren.asStateFlow()

    fun loadTags(dbType: String?) {
        if (_tagsWithChildren.value == null) {
            viewModelScope.launch {
                _tagsWithChildren.value = tagsRepository.getTagsWithChildren(dbType)
            }
        }
    }
}
