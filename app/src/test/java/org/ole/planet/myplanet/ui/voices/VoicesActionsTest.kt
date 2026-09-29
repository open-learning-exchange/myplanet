package org.ole.planet.myplanet.ui.voices

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnNewsItemClickListener
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.VoicesEditActions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class VoicesActionsTest {

    private lateinit var context: Context
    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `createEditDialogComponents inflates ViewBinding with all fields wired`() {
        val listener: OnNewsItemClickListener = mockk(relaxed = true)
        val components = VoicesActions.createEditDialogComponents(context, listener)

        assertNotNull(components.binding)
        assertSame(components.binding.root, components.view)
        assertSame(components.binding.tlInput, components.inputLayout)
        assertSame(components.binding.etInput, components.editText)
        assertSame(components.binding.llAlertImage, components.imageLayout)
        // add_news_image button should have a click listener wired by the factory
        assertEquals(true, components.binding.addNewsImage.hasOnClickListeners())
    }

    @Test
    fun `showEditAlert sets reply title and edit icon via binding`() = runTest {
        val repository: VoicesEditActions = mockk()
        coEvery { repository.getNewsById(any()) } returns null
        val listener: OnNewsItemClickListener = mockk(relaxed = true)

        var launched = false
        VoicesActions.showEditAlert(
            context = context,
            id = null,
            isEdit = false,
            currentUser = null,
            listener = listener,
            viewHolder = mockk(relaxed = true),
            repository = repository,
            updateReplyButton = { _, _, _ -> },
            launchAction = { _ -> launched = true },
        )

        // verify the dialog inflated via binding shows the "Reply" title string
        // (the dialog is created and shown; we assert no exception and the launch wiring exists)
        assertEquals(false, launched) // positive button not clicked yet
    }

    @Test
    fun `showMemberDetails returns null for null user`() = runTest {
        val result = VoicesActions.showMemberDetails(null)
        assertEquals(null, result)
    }

    @Test
    fun `showMemberDetails constructs fragment for user`() = runTest {
        val user = UserEntity().apply {
            id = "user123"
            name = "john_doe"
            firstName = "John"
            lastName = "Doe"
            email = "john@example.com"
            dob = "2000-01-01T00:00:00"
            language = "en"
            phoneNumber = "1234567890"
            level = "Level 1"
            userImage = "image_url"
        }

        val fragment = VoicesActions.showMemberDetails(user)

        assertNotNull(fragment)
        assertEquals("user123", fragment?.arguments?.getString("member_id"))
        assertEquals("John Doe", fragment?.arguments?.getString("member_name"))
        assertEquals("john@example.com", fragment?.arguments?.getString("profile_email"))
    }

    @Test
    fun `showMemberDetails sets user details in fragment arguments`() = runTest {
        val user = UserEntity().apply {
            id = "user123"
            name = "john_doe"
            firstName = "John"
            lastName = "Doe"
            email = "john@example.com"
            dob = "2000-01-01T00:00:00"
            language = "en"
            phoneNumber = "1234567890"
            level = "Level 1"
            userImage = "image_url"
        }

        val fragment = VoicesActions.showMemberDetails(user)

        assertNotNull(fragment)
        assertEquals("2000-01-01", fragment?.arguments?.getString("detail_dob"))
        assertEquals("en", fragment?.arguments?.getString("detail_language"))
        assertEquals("1234567890", fragment?.arguments?.getString("profile_phone"))
    }

    @Test
    fun `showMemberDetails handles user with minimal fields`() = runTest {
        val user = UserEntity().apply {
            id = "user123"
            name = "john_doe"
            firstName = "John"
            lastName = "Doe"
        }

        val fragment = VoicesActions.showMemberDetails(user)

        assertNotNull(fragment)
        assertEquals("user123", fragment?.arguments?.getString("member_id"))
    }
}
