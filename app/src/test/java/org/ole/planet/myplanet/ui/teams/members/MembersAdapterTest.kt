package org.ole.planet.myplanet.ui.teams.members

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnMemberActionListener
import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.ui.teams.members.MembersAdapter.Companion.PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED

@RunWith(AndroidJUnit4::class)
class MembersAdapterTest {

    private lateinit var adapter: MembersAdapter
    private lateinit var actionListener: OnMemberActionListener
    private val currentUserId = "user1"

    @Before
    fun setUp() {
        actionListener = mockk(relaxed = true)
        adapter = MembersAdapter(ApplicationProvider.getApplicationContext(), currentUserId, actionListener)
    }

    @Test
    fun testUpdateDataLeaderStatusChanged_emitsPayload() {
        val user1 = UserEntity(
            id = "user1",
            name = "User 1"
        )
        val user2 = UserEntity(
            id = "user2",
            name = "User 2"
        )
        val list = listOf(
            JoinedMemberData(user1, 0, null, true),
            JoinedMemberData(user2, 0, null, false)
        )

        var payloadEmitted: Any? = null
        val observer = object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                payloadEmitted = payload
            }
        }
        adapter.registerAdapterDataObserver(observer)

        adapter.submitList(list) {
            adapter.updateData(list, true) // Should emit payload because isLoggedInUserTeamLeader defaults to false
            assertEquals(PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED, payloadEmitted)
        }
    }

    @Test
    fun testOnBindViewHolder_withLeaderChangedPayload_updatesMenuVisibility_loggedInUser() {
        val user1 = UserEntity(
            id = "user1",
            name = "User 1"
        )
        val user2 = UserEntity(
            id = "user2",
            name = "User 2"
        )
        val multiList = listOf(
            JoinedMemberData(user1, 0, null, true), // Logged in user
            JoinedMemberData(user2, 0, null, false)
        )

        adapter.submitList(multiList) {
            adapter.updateData(multiList, true)

            val parent = FrameLayout(ApplicationProvider.getApplicationContext())
            val viewHolder1 = adapter.onCreateViewHolder(parent, 0)

            adapter.onBindViewHolder(viewHolder1, 0)

            assertEquals(View.VISIBLE, viewHolder1.binding.icMore.visibility)

            adapter.updateData(multiList, false)

            val payloads = mutableListOf<Any>(PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED)
            adapter.onBindViewHolder(viewHolder1, 0, payloads)

            assertEquals(View.VISIBLE, viewHolder1.binding.icMore.visibility)
        }
    }

    @Test
    fun testOnBindViewHolder_withLeaderChangedPayload_updatesMenuVisibility_otherUser() {
        val user1 = UserEntity(
            id = "user1",
            name = "User 1"
        )
        val user2 = UserEntity(
            id = "user2",
            name = "User 2"
        )
        val multiList = listOf(
            JoinedMemberData(user1, 0, null, true),
            JoinedMemberData(user2, 0, null, false)
        )

        adapter.submitList(multiList) {
            adapter.updateData(multiList, true)

            val parent = FrameLayout(ApplicationProvider.getApplicationContext())
            val viewHolder2 = adapter.onCreateViewHolder(parent, 1)

            adapter.onBindViewHolder(viewHolder2, 1)

            assertEquals(View.VISIBLE, viewHolder2.binding.icMore.visibility)

            adapter.updateData(multiList, false)

            val payloads = mutableListOf<Any>(PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED)
            adapter.onBindViewHolder(viewHolder2, 1, payloads)

            assertEquals(View.GONE, viewHolder2.binding.icMore.visibility)
        }
    }

    @Test
    fun testOnBindViewHolder_bindsMemberDisplayName() {
        val user = UserEntity(
            id = "user1",
            name = "Alice Example"
        )
        val list = listOf(
            JoinedMemberData(user, 0, null, true)
        )

        adapter.submitList(list) {
            val parent = FrameLayout(ApplicationProvider.getApplicationContext())
            val viewHolder = adapter.onCreateViewHolder(parent, 0)

            adapter.onBindViewHolder(viewHolder, 0)

            assertEquals("Alice Example", viewHolder.binding.tvTitle.text.toString())
        }
    }

    @Test
    fun testOnBindViewHolder_nullNameRendersEmpty() {
        val user = UserEntity(
            id = "user1",
            name = null
        )
        val list = listOf(
            JoinedMemberData(user, 0, null, true)
        )

        adapter.submitList(list) {
            val parent = FrameLayout(ApplicationProvider.getApplicationContext())
            val viewHolder = adapter.onCreateViewHolder(parent, 0)

            adapter.onBindViewHolder(viewHolder, 0)

            assertEquals("", viewHolder.binding.tvTitle.text.toString())
        }
    }

    @Test
    fun testOnBindViewHolder_withUnknownPayload_fallsBackToFullBind() {
        val user1 = UserEntity(
            id = "user1",
            name = "User 1"
        )
        val list = listOf(
            JoinedMemberData(user1, 0, null, true)
        )

        adapter.submitList(list) {
            val parent = FrameLayout(ApplicationProvider.getApplicationContext())
            val viewHolder = adapter.onCreateViewHolder(parent, 0)

            val payloads = mutableListOf<Any>("UNKNOWN_PAYLOAD")

            viewHolder.binding.tvTitle.text = ""

            adapter.onBindViewHolder(viewHolder, 0, payloads)

            assertEquals("User 1", viewHolder.binding.tvTitle.text.toString())
        }
    }

    @Test
    fun testOnBindViewHolder_lastVisitDate_usesSharedTimeUtilsFormatter() {
        // Pin zone/locale so the rendered short date is deterministic. getFormattedShortDate
        // renders in the system-default zone, so a UTC midnight timestamp stays on its day
        // and avoids the off-by-one that a localized zone would introduce.
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val user1 = UserEntity(
                id = "user1",
                name = "User 1"
            )
            // March 11, 2024, 00:00:00 UTC -> "11 Mar 2024" in the short (dd MMM yyyy) format
            val timestamp = 1710115200000L
            val list = listOf(
                JoinedMemberData(user1, 0, timestamp, false)
            )

            adapter.submitList(list) {
                val parent = FrameLayout(ApplicationProvider.getApplicationContext())
                val viewHolder = adapter.onCreateViewHolder(parent, 0)

                adapter.onBindViewHolder(viewHolder, 0)

                // Literal expectation, not a re-call of the function under test: this fails if
                // the adapter switches pattern or timezone (the regression the issue guards against).
                assertTrue(viewHolder.binding.tvLastVisit.text.toString().contains("11 Mar 2024"))
            }
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun testOnBindViewHolder_nullLastVisitDate_showsNoVisit() {
        val user1 = UserEntity(
            id = "user1",
            name = "User 1"
        )
        val list = listOf(
            JoinedMemberData(user1, 0, null, false)
        )

        adapter.submitList(list) {
            val parent = FrameLayout(ApplicationProvider.getApplicationContext())
            val viewHolder = adapter.onCreateViewHolder(parent, 0)

            adapter.onBindViewHolder(viewHolder, 0)

            // When lastVisitDate is null, no_visit string is used, not a formatted date.
            val expected = ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(org.ole.planet.myplanet.R.string.no_visit)
            assertTrue(viewHolder.binding.tvLastVisit.text.toString().contains(expected))
        }
    }

    @Test
    fun testOnBindViewHolder_hoistedStringsResolvedOnceAcrossMultipleBinds() {
        val spyContext = spyk(ApplicationProvider.getApplicationContext<android.content.Context>())
        val spyAdapter = MembersAdapter(spyContext, currentUserId, actionListener)

        val leaderUser = UserEntity(id = "user1", name = "Leader")
        val memberUser = UserEntity(id = "user2", name = "Member")
        val list = listOf(
            JoinedMemberData(leaderUser, 0, null, isLeader = true),
            JoinedMemberData(memberUser, 0, null, isLeader = false)
        )

        spyAdapter.submitList(list) {
            val parent = FrameLayout(spyContext)
            val vh0 = spyAdapter.onCreateViewHolder(parent, 0)
            val vh1 = spyAdapter.onCreateViewHolder(parent, 0)

            spyAdapter.onBindViewHolder(vh0, 0)
            spyAdapter.onBindViewHolder(vh1, 1)

            verify(exactly = 1) { spyContext.getString(org.ole.planet.myplanet.R.string.team_leader) }
            verify(exactly = 1) { spyContext.getString(org.ole.planet.myplanet.R.string.no_visit) }
        }
    }

    @Test
    fun removeFromMenu_afterEarlierRowRemoved_actsOnTheMemberShownInThatRow() {
        val activity = org.robolectric.Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val leaderAdapter = MembersAdapter(activity, currentUserId, actionListener)
        val recyclerView = androidx.recyclerview.widget.RecyclerView(activity).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
            adapter = leaderAdapter
        }
        activity.setContentView(recyclerView)
        val me = JoinedMemberData(UserEntity(id = "user1", name = "Me"), 0, null, true)
        val bob = JoinedMemberData(UserEntity(id = "bob", name = "Bob"), 0, null, false)
        val carol = JoinedMemberData(UserEntity(id = "carol", name = "Carol"), 0, null, false)

        fun submitAndLayout(list: List<JoinedMemberData>) {
            var done = false
            leaderAdapter.updateData(list, true)
            leaderAdapter.submitList(list) { done = true }
            while (!done) org.robolectric.shadows.ShadowLooper.idleMainLooper()
            recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            recyclerView.layout(0, 0, 1080, 1920)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }

        submitAndLayout(listOf(me, bob, carol))
        val carolRow = recyclerView.findViewHolderForAdapterPosition(2) as MembersAdapter.MembersViewHolder

        // Bob is removed; Carol's row moves up to position 1 without being rebound
        submitAndLayout(listOf(me, carol))
        assertEquals(1, carolRow.bindingAdapterPosition)

        carolRow.binding.icMore.performClick()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        dialog.listView.performItemClick(null, 0, 0)

        verify { actionListener.onRemoveMember(carol, 1) }
    }
}
