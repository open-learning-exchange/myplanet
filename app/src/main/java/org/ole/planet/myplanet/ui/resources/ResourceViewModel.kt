package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.TagRepository
import javax.inject.Inject

@HiltViewModel
class ResourceViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val application: Application
) : ViewModel() {

    private val _tagSpannables = MutableLiveData<Map<String, SpannableString>>()
    val tagSpannables: LiveData<Map<String, SpannableString>> = _tagSpannables

    private val tagCache = mutableMapOf<String, SpannableString>()
    private val tagRequestsInProgress = mutableSetOf<String>()

    fun preloadTagsForResources(resourceIds: List<String>) {
        viewModelScope.launch {
            val newTags = mutableMapOf<String, SpannableString>()
            for (resourceId in resourceIds) {
                if (!tagCache.containsKey(resourceId) && !tagRequestsInProgress.contains(resourceId)) {
                    tagRequestsInProgress.add(resourceId)
                    val tags = withContext(Dispatchers.IO) {
                        tagRepository.getTagsForResource(resourceId)
                    }
                    val spannable = createSpannableForTags(tags)
                    tagCache[resourceId] = spannable
                    newTags[resourceId] = spannable
                    tagRequestsInProgress.remove(resourceId)
                }
            }
            if (newTags.isNotEmpty()) {
                _tagSpannables.postValue(tagCache)
            }
        }
    }

    private fun createSpannableForTags(tags: List<RealmTag>): SpannableString {
        val tagNames = tags.joinToString(" ") { it.name ?: "" }
        val spannable = SpannableString(tagNames)
        var start = 0
        val tagColor = ContextCompat.getColor(application, R.color.md_grey_300)

        for (tag in tags) {
            val tagName = tag.name ?: ""
            val end = start + tagName.length
            if (start < end) {
                spannable.setSpan(
                    BackgroundColorSpan(tagColor),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            start = end + 1
        }
        return spannable
    }
}
