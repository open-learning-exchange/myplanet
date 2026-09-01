package org.ole.planet.myplanet.ui.teams.members

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnMemberActionListener
import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.ui.teams.members.MembersAdapter.Companion.PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED
import org.ole.planet.myplanet.utils.TimeUtils
import java.util.Locale
import java.util.TimeZone

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
            JoinedMemberData(user1, 0, null, "", "", true),
            JoinedMemberData(user2, 0, null, "", "", false)
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
            JoinedMemberData(user1, 0, null, "", "", true), // Logged in user
            JoinedMemberData(user2, 0, null, "", "", false)
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
            JoinedMemberData(user1, 0, null, "", "", true),
            JoinedMemberData(user2, 0, null, "", "", false)
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
            JoinedMemberData(user, 0, null, "", "", true)
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
            JoinedMemberData(user, 0, null, "", "", true)
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
            JoinedMemberData(user1, 0, null, "", "", true)
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
                JoinedMemberData(user1, 0, timestamp, "", "", false)
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
            JoinedMemberData(user1, 0, null, "", "", false)
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
}
