package org.ole.planet.myplanet.ui.teams.members

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnMemberActionListener
import org.ole.planet.myplanet.model.JoinedMemberData
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.ui.teams.members.MembersAdapter.Companion.PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
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

        // Use synchronous update for initial state
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

        // We set the list synchronously via updateData first to have data in getItem
        adapter.submitList(multiList) {
            adapter.updateData(multiList, true)

            val parent = FrameLayout(ApplicationProvider.getApplicationContext())
            val viewHolder1 = adapter.onCreateViewHolder(parent, 0)

            // force full bind first to set up views directly calling onBindViewHolder
            adapter.onBindViewHolder(viewHolder1, 0)

            // Logged-in user should always have the menu visible if itemCount > 1, regardless of leader status
            assertEquals(View.VISIBLE, viewHolder1.binding.icMore.visibility)

            // Apply payload explicitly to test partial update independently
            // We change the leader status directly
            adapter.updateData(multiList, false)

            val payloads = mutableListOf<Any>(PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED)
            adapter.onBindViewHolder(viewHolder1, 0, payloads)

            // Should still be visible for own card
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

            // force full bind first to set up views
            adapter.onBindViewHolder(viewHolder2, 1)

            assertEquals(View.VISIBLE, viewHolder2.binding.icMore.visibility)

            // Change leader status to false
            adapter.updateData(multiList, false)

            // Apply payload explicitly
            val payloads = mutableListOf<Any>(PAYLOAD_KEY_LOGGED_IN_USER_LEADER_CHANGED)
            adapter.onBindViewHolder(viewHolder2, 1, payloads)

            assertEquals(View.GONE, viewHolder2.binding.icMore.visibility)
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

            // Test falling back when payloads contains only unknown elements
            val payloads = mutableListOf<Any>("UNKNOWN_PAYLOAD")

            // Ensure title starts empty to verify full bind happens
            viewHolder.binding.tvTitle.text = ""

            adapter.onBindViewHolder(viewHolder, 0, payloads)

            // Title should be updated if full bind is correctly called as a fallback
            assertEquals("User 1", viewHolder.binding.tvTitle.text.toString())
        }
    }
}
