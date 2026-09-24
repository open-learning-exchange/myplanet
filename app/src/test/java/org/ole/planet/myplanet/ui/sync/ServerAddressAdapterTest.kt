package org.ole.planet.myplanet.ui.sync

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.ServerAddress
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ServerAddressAdapterTest {

    private lateinit var adapter: ServerAddressAdapter
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
        adapter = ServerAddressAdapter(
            context = context,
            onItemClick = {},
            onClearDataDialog = { _, _ -> },
            isServerAlreadyConfigured = false,
        )
    }

    @Test
    fun `onCreateViewHolder inflates ViewBinding holder with button`() {
        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        assertNotNull(holder.binding)
        assertNotNull(holder.binding.btnServerAddress)
    }

    @Test
    fun `onBindViewHolder binds server name to button text`() {
        val address = ServerAddress(name = "Planet Learning", url = "https://planet.learning")
        adapter.submitList(listOf(address))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0) as ServerAddressAdapter.ViewHolder
        adapter.onBindViewHolder(holder, 0)

        assertEquals("Planet Learning", holder.binding.btnServerAddress.text.toString())
    }

    @Test
    fun `updateSelectionState applies selected color when selected`() {
        val address = ServerAddress(name = "Server", url = "https://server")
        adapter.submitList(listOf(address))
        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0) as ServerAddressAdapter.ViewHolder
        adapter.onBindViewHolder(holder, 0)

        holder.updateSelectionState(true)
        assertEquals(true, holder.binding.btnServerAddress.isSelected)

        holder.updateSelectionState(false)
        assertEquals(false, holder.binding.btnServerAddress.isSelected)
    }

    @Test
    fun `setSelectedPosition notifies change without crashing`() {
        adapter.submitList(
            listOf(
                ServerAddress("A", "https://a"),
                ServerAddress("B", "https://b"),
            )
        )
        adapter.setSelectedPosition(1)
        adapter.revertSelection()
        adapter.clearSelection()
        // selection payload path rebinds the holder without full rebind
        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0, mutableListOf("selection_payload"))
    }

    @Test
    fun `reorder calls onClearDataDialog with current adapter position and server`() {
        var clearDataCalledWithServer: ServerAddress? = null
        var clearDataCalledWithPos: Int? = null

        val testAdapter = ServerAddressAdapter(
            context = context,
            onItemClick = {},
            onClearDataDialog = { server, pos ->
                clearDataCalledWithServer = server
                clearDataCalledWithPos = pos
            },
            isServerAlreadyConfigured = true,
        )

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = testAdapter

        val serverA = ServerAddress("A", "https://a")
        val serverB = ServerAddress("B", "https://b")
        val serverC = ServerAddress("C", "https://c")

        val widthSpec = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)

        testAdapter.setSelectedPosition(0)

        var committed = false
        testAdapter.submitList(listOf(serverA, serverB)) { committed = true }
        while (!committed) { ShadowLooper.idleMainLooper() }
        recyclerView.measure(widthSpec, heightSpec)
        recyclerView.layout(0, 0, 1000, 1000)

        committed = false
        testAdapter.submitList(listOf(serverB, serverA, serverC)) { committed = true }
        while (!committed) { ShadowLooper.idleMainLooper() }
        recyclerView.measure(widthSpec, heightSpec)
        recyclerView.layout(0, 0, 1000, 1000)

        val holderA = recyclerView.findViewHolderForAdapterPosition(1)
        assertNotNull(holderA)

        holderA!!.itemView.performClick()

        assertEquals(serverA, clearDataCalledWithServer)
        assertEquals(1, clearDataCalledWithPos)
    }
}
