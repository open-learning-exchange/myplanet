package org.ole.planet.myplanet.utilities

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.TagRepository

class TagBatchLoader(
    private val tagRepository: TagRepository,
    private val lifecycleOwner: LifecycleOwner,
    private val onTagsLoaded: (List<String>) -> Unit
) {
    private val tagCache = mutableMapOf<String, List<RealmTag>>()
    private var batchJob: Job? = null

    fun getTags(id: String): List<RealmTag>? = tagCache[id]

    fun attachTo(recyclerView: RecyclerView, idProvider: (Int) -> String?) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    loadVisibleTags(recyclerView, idProvider)
                }
            }
        })
    }

    private fun loadVisibleTags(recyclerView: RecyclerView, idProvider: (Int) -> String?) {
        batchJob?.cancel()
        batchJob = lifecycleOwner.lifecycleScope.launch {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@launch
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()

            if (firstVisible == RecyclerView.NO_POSITION) return@launch

            val idsToLoad = (firstVisible..lastVisible)
                .mapNotNull { idProvider(it) }
                .filterNot { tagCache.containsKey(it) }

            if (idsToLoad.isNotEmpty()) {
                val newTags = tagRepository.getTagsForMultipleResources(idsToLoad)
                tagCache.putAll(newTags)
                onTagsLoaded(idsToLoad)
            }
        }
    }
}
