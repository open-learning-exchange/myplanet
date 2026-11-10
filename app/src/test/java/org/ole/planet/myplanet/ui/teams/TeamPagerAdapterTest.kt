package org.ole.planet.myplanet.ui.teams

import androidx.fragment.app.Fragment
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnChangedListener
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TeamPagerAdapterTest {

    @Test
    fun `containsItem tests and page update behavior`() {
        val hostFragment = mockk<Fragment>(relaxed = true)
        val memberChangeListener = mockk<OnChangedListener>(relaxed = true)
        val teamUpdateListener = mockk<OnChangedListener>(relaxed = true)

        val initialPages = listOf(
            TeamPageConfig.TeamPage,
            TeamPageConfig.MembersPage,
            TeamPageConfig.CoursesPage
        )

        val adapter = TeamPagerAdapter(
            parentFragment = hostFragment,
            pages = initialPages,
            teamId = "team_1",
            onMemberChangeListener = memberChangeListener,
            teamUpdateListener = teamUpdateListener
        )

        // ids of the constructor pages are contained before any updatePages call (checks init seeding)
        val teamPageId = adapter.getItemId(0)
        val membersPageId = adapter.getItemId(1)
        val coursesPageId = adapter.getItemId(2)

        assertTrue(adapter.containsItem(teamPageId))
        assertTrue(adapter.containsItem(membersPageId))
        assertTrue(adapter.containsItem(coursesPageId))
        assertFalse(adapter.containsItem(999L))

        // After updatePages drops a page, that page's id returns false and the remaining pages are unchanged
        val newPages = listOf(
            TeamPageConfig.TeamPage,
            TeamPageConfig.CoursesPage
        )
        adapter.updatePages(newPages)

        assertTrue(adapter.containsItem(teamPageId))
        assertFalse(adapter.containsItem(membersPageId))
        assertTrue(adapter.containsItem(coursesPageId))
        assertTrue(adapter.getItemId(0) == teamPageId)
        assertTrue(adapter.getItemId(1) == coursesPageId)
    }
}
