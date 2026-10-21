package org.ole.planet.myplanet.ui.voices

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.repository.VoicesEditor
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VoicesAdapterImagesTest {

    private lateinit var context: android.content.Context
    private lateinit var adapter: VoicesAdapter
    private val requestedResourceIds = mutableListOf<String>()
    private val pendingLibraryCallbacks = mutableListOf<(MyLibrary?) -> Unit>()
    private var deferLibraryCallbacks = false

    @Before
    fun setUp() {
        requestedResourceIds.clear()
        pendingLibraryCallbacks.clear()
        deferLibraryCallbacks = false
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        context = ContextThemeWrapper(base, R.style.MyMaterialTheme)
        adapter = VoicesAdapter(
            context = context,
            currentUser = null,
            parentNews = null,
            teamName = "",
            teamId = null,
            isTeamLeaderFn = { },
            getUserFn = { _, _ -> },
            getReplyCountFn = { _, _ -> { } },
            deletePostFn = { },
            shareNewsFn = { _, _, _, _, _ -> },
            getLibraryResourceFn = { resourceId, cb ->
                requestedResourceIds.add(resourceId)
                if (deferLibraryCallbacks) pendingLibraryCallbacks.add(cb) else cb(null)
            },
            onEditAction = { },
            onAnimateTyping = { _, _, _ -> { } },
            labelManager = io.mockk.mockk(relaxed = true),
            voicesEditor = io.mockk.mockk<VoicesEditor>(relaxed = true),
            leadersList = emptyList(),
        )
    }

    private fun bindForImages(payload: String, images: String?): org.ole.planet.myplanet.databinding.RowNewsBinding {
        val news = News().apply {
            id = "n1"
            this.images = images
        }
        var committed = false
        adapter.submitList(listOf(news)) { committed = true }
        while (!committed) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0, mutableListOf(payload))

        val binding = (holder as VoicesAdapter.VoicesViewHolder).binding
        assertEquals(View.GONE, binding.imgNews.visibility)
        return binding
    }

    private fun imagesJson(vararg resourceIds: String): String {
        val arr = com.google.gson.JsonArray()
        for (id in resourceIds) {
            val obj = com.google.gson.JsonObject()
            obj.addProperty("resourceId", id)
            arr.add(obj)
        }
        return arr.toString()
    }

    @Test
    fun `empty imagesArray renders neither image view nor image container`() {
        val binding = bindForImages(VoicesAdapter.PAYLOAD_IMAGES_CHANGED, "[]")

        assertEquals(View.GONE, binding.imgNews.visibility)
        assertEquals(View.GONE, binding.llNewsImages.visibility)
        assertTrue(requestedResourceIds.isEmpty())
    }

    @Test
    fun `single image array entry requests exactly that resourceId`() {
        bindForImages(VoicesAdapter.PAYLOAD_IMAGES_CHANGED, imagesJson("res-only"))

        assertEquals(listOf("res-only"), requestedResourceIds)
    }

    @Test
    fun `multiple image array entries show the image container and request each resourceId in order`() {
        val binding = bindForImages(VoicesAdapter.PAYLOAD_IMAGES_CHANGED, imagesJson("res-a", "res-b"))

        assertEquals(View.VISIBLE, binding.llNewsImages.visibility)
        assertEquals(listOf("res-a", "res-b"), requestedResourceIds)
    }

    private fun submitAndAwait(news: List<News>) {
        var committed = false
        adapter.submitList(news) { committed = true }
        while (!committed) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
    }

    private fun library(id: String) = MyLibrary().apply {
        this.id = id
        resourceLocalAddress = "$id.png"
    }

    @Test
    fun `library image resolved after the row is rebound to a post without images is ignored`() {
        deferLibraryCallbacks = true
        submitAndAwait(listOf(News().apply { id = "n1"; images = imagesJson("res-old") }))
        val holder = adapter.onCreateViewHolder(LinearLayout(context), 0)
        adapter.onBindViewHolder(holder, 0, mutableListOf(VoicesAdapter.PAYLOAD_IMAGES_CHANGED))

        submitAndAwait(listOf(News().apply { id = "n2"; images = "[]" }))
        adapter.onBindViewHolder(holder, 0, mutableListOf(VoicesAdapter.PAYLOAD_IMAGES_CHANGED))
        pendingLibraryCallbacks.forEach { it(library("res-old")) }

        val binding = (holder as VoicesAdapter.VoicesViewHolder).binding
        assertEquals(View.GONE, binding.imgNews.visibility)
    }

    @Test
    fun `rebinding a multi-image post while images resolve does not duplicate thumbnails`() {
        deferLibraryCallbacks = true
        submitAndAwait(listOf(News().apply { id = "n1"; images = imagesJson("res-a", "res-b") }))
        val holder = adapter.onCreateViewHolder(LinearLayout(context), 0)
        adapter.onBindViewHolder(holder, 0, mutableListOf(VoicesAdapter.PAYLOAD_IMAGES_CHANGED))
        adapter.onBindViewHolder(holder, 0, mutableListOf(VoicesAdapter.PAYLOAD_IMAGES_CHANGED))

        pendingLibraryCallbacks.forEachIndexed { i, callback -> callback(library("res-$i")) }

        val binding = (holder as VoicesAdapter.VoicesViewHolder).binding
        assertEquals(4, pendingLibraryCallbacks.size)
        assertEquals(2, binding.llNewsImages.childCount)
    }
}
