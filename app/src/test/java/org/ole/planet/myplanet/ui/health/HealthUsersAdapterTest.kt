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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.TimeUtils

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
        verify(exactly = 0) { viewHolder.bind(any(), any()) }
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
        verify(exactly = 0) { viewHolder.bind(any(), any()) }
    }

    @Test
    fun testPayload_somethingElse_fallsBackToFullBind() {
        val parent = FrameLayout(context)
        val viewHolder = spyk(adapter.onCreateViewHolder(parent, 0))
        clearMocks(viewHolder)

        val payloads = mutableListOf<Any>(listOf("somethingElse"))
        adapter.onBindViewHolder(viewHolder, 0, payloads)

        verify(exactly = 1) { viewHolder.bind(testUser, any()) }
    }

    @Test
    fun testPayload_nonListPayload_fallsBackToFullBind() {
        val parent = FrameLayout(context)
        val viewHolder = spyk(adapter.onCreateViewHolder(parent, 0))
        clearMocks(viewHolder)

        val payloads = mutableListOf<Any>("somethingElse")
        adapter.onBindViewHolder(viewHolder, 0, payloads)

        verify(exactly = 1) { viewHolder.bind(testUser, any()) }
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
        verify(exactly = 0) { viewHolder.bind(any(), any()) }
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
