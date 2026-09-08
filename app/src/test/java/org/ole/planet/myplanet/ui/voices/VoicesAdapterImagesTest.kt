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
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.repository.VoicesEditActions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VoicesAdapterImagesTest {

    private lateinit var context: android.content.Context
    private lateinit var adapter: VoicesAdapter
    private val requestedResourceIds = mutableListOf<String>()

    @Before
    fun setUp() {
        requestedResourceIds.clear()
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
                cb(null)
            },
            onEditAction = { },
            onAnimateTyping = { _, _, _ -> { } },
            labelManager = io.mockk.mockk(relaxed = true),
            voicesEditActions = io.mockk.mockk<VoicesEditActions>(relaxed = true),
            leadersList = emptyList(),
            setRepliedNewsIdFn = { },
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
}
