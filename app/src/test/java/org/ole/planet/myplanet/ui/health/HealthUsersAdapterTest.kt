package org.ole.planet.myplanet.ui.health

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.TimeUtils
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class HealthUsersAdapterTest {

    private lateinit var adapter: HealthUsersAdapter
    private lateinit var context: Context
    private val testUser = UserEntity(
        id = "user1",
        name = "John Doe",
        userImage = "profile.png",
        joinDate = 1000L
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        adapter = HealthUsersAdapter()
        adapter.submitList(listOf(testUser))
    }

    @Test
    fun testPayload_listOfName_callsOnlyBindName() {
        val parent = FrameLayout(context)
        val viewHolder = spyk(adapter.onCreateViewHolder(parent, 0))
        clearMocks(viewHolder)

        val payloads = mutableListOf<Any>(listOf("name"))
        adapter.onBindViewHolder(viewHolder, 0, payloads)

        verify(exactly = 1) { viewHolder.bindName(testUser) }
        verify(exactly = 0) { viewHolder.bindImage(any()) }
        verify(exactly = 0) { viewHolder.bindDate(any()) }
        verify(exactly = 0) { viewHolder.bind(any()) }
    }

    @Test
    fun testPayload_twoKeys_callsBothBindsInOrder() {
        val parent = FrameLayout(context)
        val viewHolder = spyk(adapter.onCreateViewHolder(parent, 0))
        clearMocks(viewHolder)

        val payloads = mutableListOf<Any>(listOf("name", "joinDate"))
        adapter.onBindViewHolder(viewHolder, 0, payloads)

        verifyOrder {
            viewHolder.bindName(testUser)
            viewHolder.bindDate(testUser)
        }
        verify(exactly = 0) { viewHolder.bindImage(any()) }
        verify(exactly = 0) { viewHolder.bind(any()) }
    }

    @Test
    fun testPayload_somethingElse_fallsBackToFullBind() {
        val parent = FrameLayout(context)
        val viewHolder = spyk(adapter.onCreateViewHolder(parent, 0))
        clearMocks(viewHolder)

        val payloads = mutableListOf<Any>(listOf("somethingElse"))
        adapter.onBindViewHolder(viewHolder, 0, payloads)

        verify(exactly = 1) { viewHolder.bind(testUser) }
    }

    @Test
    fun testPayload_nonListPayload_fallsBackToFullBind() {
        val parent = FrameLayout(context)
        val viewHolder = spyk(adapter.onCreateViewHolder(parent, 0))
        clearMocks(viewHolder)

        val payloads = mutableListOf<Any>("somethingElse")
        adapter.onBindViewHolder(viewHolder, 0, payloads)

        verify(exactly = 1) { viewHolder.bind(testUser) }
    }

    @Test
    fun testPayload_duplicateKeysAcrossTwoPayloadEntries_bindsOnceEach() {
        val parent = FrameLayout(context)
        val viewHolder = spyk(adapter.onCreateViewHolder(parent, 0))
        clearMocks(viewHolder)

        val payloads = mutableListOf<Any>(listOf("name"), listOf("name"))
        adapter.onBindViewHolder(viewHolder, 0, payloads)

        verify(exactly = 1) { viewHolder.bindName(testUser) }
        verify(exactly = 0) { viewHolder.bindImage(any()) }
        verify(exactly = 0) { viewHolder.bindDate(any()) }
        verify(exactly = 0) { viewHolder.bind(any()) }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testFirstNameChange_yieldsNamePayloadAndAreContentsTheSameFalse() {
        val u1 = UserEntity(id = "user1", name = "john", firstName = "John", lastName = "Doe", joinDate = 1000L)
        val u1Updated = UserEntity(id = "user1", name = "john", firstName = "Johnny", lastName = "Doe", joinDate = 1000L)

        val diffCallback = HealthUsersAdapter::class.java.getDeclaredField("DIFF_CALLBACK").apply {
            isAccessible = true
        }.get(null) as androidx.recyclerview.widget.DiffUtil.ItemCallback<UserEntity>

        assertFalse(diffCallback.areContentsTheSame(u1, u1Updated))
        val payload = diffCallback.getChangePayload(u1, u1Updated) as List<*>
        assertEquals(listOf("name"), payload)
    }

    @Test
    fun testClickListener_deliversUpdatedUserAfterPayloadBind() {
        var clickedUser: UserEntity? = null
        val testAdapter = HealthUsersAdapter { user -> clickedUser = user }

        val u1 = UserEntity(id = "user1", name = "john", firstName = "John", lastName = "Doe", joinDate = 1000L)
        val u1Updated = UserEntity(id = "user1", name = "john", firstName = "John", lastName = "Smith", joinDate = 1000L)

        val recyclerView = androidx.recyclerview.widget.RecyclerView(context)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerView.adapter = testAdapter

        var committed = false
        testAdapter.submitList(listOf(u1)) {
            committed = true
        }
        while (!committed) {
            ShadowLooper.idleMainLooper()
        }

        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1000, 1000)

        val viewHolder = recyclerView.findViewHolderForAdapterPosition(0) as HealthUsersAdapter.ViewHolder

        var committed2 = false
        testAdapter.submitList(listOf(u1Updated)) {
            committed2 = true
        }
        while (!committed2) {
            ShadowLooper.idleMainLooper()
        }

        testAdapter.onBindViewHolder(viewHolder, 0, mutableListOf(listOf("name")))

        viewHolder.itemView.performClick()

        assertEquals(u1Updated, clickedUser)
        assertEquals("Smith", clickedUser?.lastName)
    }

    @Test
    fun testBindDate_cachesFormattedDateForSameJoinDate() {
        mockkObject(TimeUtils)
        try {
            every { TimeUtils.formatDate(1000L) } answers { callOriginal() }

            val localAdapter = HealthUsersAdapter()
            val user1 = UserEntity(id = "user1", name = "User 1", joinDate = 1000L)
            val user2 = UserEntity(id = "user2", name = "User 2", joinDate = 1000L)

            localAdapter.submitList(listOf(user1, user2)) {
                val parent = FrameLayout(context)
                val viewHolder1 = localAdapter.onCreateViewHolder(parent, 0)
                val viewHolder2 = localAdapter.onCreateViewHolder(parent, 0)

                localAdapter.onBindViewHolder(viewHolder1, 0)
                localAdapter.onBindViewHolder(viewHolder2, 1)

                verify(exactly = 1) { TimeUtils.formatDate(1000L) }
            }
        } finally {
            unmockkObject(TimeUtils)
        }
    }
}
