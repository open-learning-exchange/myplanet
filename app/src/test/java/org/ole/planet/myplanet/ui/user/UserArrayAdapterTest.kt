package org.ole.planet.myplanet.ui.user

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.clearMocks
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.TimeUtils

@RunWith(AndroidJUnit4::class)
class UserArrayAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: UserArrayAdapter

    private val user1 = UserEntity(
        id = "1",
        name = "johndoe",
        firstName = "John",
        lastName = "Doe",
        joinDate = 1600000000000L
    )

    private val user2 = UserEntity(
        id = "2",
        name = "janedoe",
        firstName = "Jane",
        lastName = "Doe",
        joinDate = 1600000000000L
    )

    private val user3 = UserEntity(
        id = "3",
        name = "bobsmith",
        firstName = "Bob",
        lastName = "Smith",
        joinDate = 1700000000000L
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        adapter = UserArrayAdapter(context) {}
        mockkObject(TimeUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(TimeUtils)
    }

    @Test
    fun testOnBindViewHolder_cachesFormattedDateForSameJoinDate() {
        val parent = FrameLayout(context)
        adapter.submitList(listOf(user1, user2, user3))

        val formattedDate1 = TimeUtils.formatDate(1600000000000L)
        val expectedJoinedText1 = context.getString(R.string.joined_colon, formattedDate1)
        val formattedDate3 = TimeUtils.formatDate(1700000000000L)
        val expectedJoinedText3 = context.getString(R.string.joined_colon, formattedDate3)

        clearMocks(TimeUtils, answers = false)

        val holder1 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder1, 0)

        val holder2 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder2, 1)

        val holder3 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder3, 2)

        assertEquals(expectedJoinedText1, holder1.binding.txtJoined.text.toString())
        assertEquals(expectedJoinedText1, holder2.binding.txtJoined.text.toString())
        assertEquals(expectedJoinedText3, holder3.binding.txtJoined.text.toString())

        verify(exactly = 1) { TimeUtils.formatDate(1600000000000L) }
        verify(exactly = 1) { TimeUtils.formatDate(1700000000000L) }
    }
}
