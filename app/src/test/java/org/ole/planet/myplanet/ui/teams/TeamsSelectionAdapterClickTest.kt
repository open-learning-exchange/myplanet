package org.ole.planet.myplanet.ui.teams

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.TeamSummary
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TeamsSelectionAdapterClickTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_MaterialComponents)
    }

    private fun createTeamSummary(id: String, name: String): TeamSummary {
        return TeamSummary(
            _id = id,
            name = name,
            teamType = null,
            teamPlanetCode = null,
            createdDate = null,
            type = null,
            status = null,
            teamId = null,
            description = null,
            services = null,
            rules = null
        )
    }

    private fun <T> submitListAndSync(adapter: ListAdapter<T, *>, list: List<T>) {
        val latch = CountDownLatch(1)
        adapter.submitList(list) {
            latch.countDown()
        }
        ShadowLooper.idleMainLooper()
        latch.await(2, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun testRebindAndClick_invokesOnClickWithUpdatedItem() {
        val onClick = mockk<(TeamSummary) -> Unit>(relaxed = true)
        val adapter = TeamsSelectionAdapter(section = context.getString(R.string.teams), onClick = onClick)

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.itemAnimator = null
        recyclerView.adapter = adapter

        val teamAlpha = createTeamSummary("1", "Alpha")
        submitListAndSync(adapter, listOf(teamAlpha))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder1 = recyclerView.findViewHolderForAdapterPosition(0)

        val teamBeta = createTeamSummary("1", "Beta")
        submitListAndSync(adapter, listOf(teamBeta))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder2 = recyclerView.findViewHolderForAdapterPosition(0)
        assertEquals(holder1, holder2)

        holder2!!.itemView.performClick()

        verify(exactly = 1) { onClick(match { it.name == "Beta" }) }
    }

    @Test
    fun testSharedItem_doesNotInvokeOnClick() {
        val onClick = mockk<(TeamSummary) -> Unit>(relaxed = true)
        val adapter = TeamsSelectionAdapter(
            section = context.getString(R.string.teams),
            sharedIds = setOf("1"),
            onClick = onClick
        )

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.itemAnimator = null
        recyclerView.adapter = adapter

        val teamAlpha = createTeamSummary("1", "Alpha")
        submitListAndSync(adapter, listOf(teamAlpha))
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 100, 100)

        val holder = recyclerView.findViewHolderForAdapterPosition(0)!!
        holder.itemView.performClick()

        verify(exactly = 0) { onClick(any()) }
    }
}
