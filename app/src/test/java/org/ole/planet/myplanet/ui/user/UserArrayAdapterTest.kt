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
        adapter.submitList(listOf(user1, user2))

        clearMocks(TimeUtils, answers = false)

        val holder1 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder1, 0)

        val holder2 = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder2, 1)

        val formattedDate = TimeUtils.formatDate(1600000000000L)
        val expectedJoinedText = context.getString(R.string.joined_colon, formattedDate)

        assertEquals(expectedJoinedText, holder1.binding.txtJoined.text.toString())
        assertEquals(expectedJoinedText, holder2.binding.txtJoined.text.toString())

        // TimeUtils.formatDate was called once in adapter onBindViewHolder for holder1
        // and once above in this test method to construct expectedJoinedText.
        verify(exactly = 2) { TimeUtils.formatDate(1600000000000L) }
    }
}
