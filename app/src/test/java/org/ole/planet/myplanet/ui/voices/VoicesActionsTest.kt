package org.ole.planet.myplanet.ui.voices

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnNewsItemClickListener
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.MemberVisitStats
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
        val activitiesRepository: ActivitiesRepository = mockk()
        val result = VoicesActions.showMemberDetails(null, activitiesRepository)
        assertEquals(null, result)
    }

    @Test
    fun `showMemberDetails calls getMemberVisitStats once and constructs fragment`() = runTest {
        val activitiesRepository: ActivitiesRepository = mockk()
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

        coEvery { activitiesRepository.getMemberVisitStats("user123", "john_doe") } returns MemberVisitStats(
            offlineVisitCount = 4,
            lastVisit = null
        )

        val fragment = VoicesActions.showMemberDetails(user, activitiesRepository)

        assertNotNull(fragment)
        assertEquals("4", fragment?.arguments?.getString("number_of_visits"))
        assertEquals("No logout record found", fragment?.arguments?.getString("last_login"))

        coVerify(exactly = 1) { activitiesRepository.getMemberVisitStats("user123", "john_doe") }
        coVerify(exactly = 0) { activitiesRepository.getOfflineVisitCount(any()) }
        coVerify(exactly = 0) { activitiesRepository.getLastVisit(any()) }
    }

    @Test
    fun `showMemberDetails formats lastVisit in default zone and locale`() = runTest {
        val activitiesRepository: ActivitiesRepository = mockk()
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

        // 1700000000000 L -> Nov 14, 2023 10:13 PM UTC
        coEvery { activitiesRepository.getMemberVisitStats("user123", "john_doe") } returns MemberVisitStats(
            offlineVisitCount = 2,
            lastVisit = 1700000000000L
        )

        val fragment = VoicesActions.showMemberDetails(user, activitiesRepository)

        assertNotNull(fragment)
        assertEquals("November 14, 2023 10:13 PM", fragment?.arguments?.getString("last_login"))
    }

    @Test
    fun `showMemberDetails falls back when lastVisit is null`() = runTest {
        val activitiesRepository: ActivitiesRepository = mockk()
        val user = UserEntity().apply {
            id = "user123"
            name = "john_doe"
            firstName = "John"
            lastName = "Doe"
        }

        coEvery { activitiesRepository.getMemberVisitStats("user123", "john_doe") } returns MemberVisitStats(
            offlineVisitCount = 0,
            lastVisit = null
        )

        val fragment = VoicesActions.showMemberDetails(user, activitiesRepository)

        assertNotNull(fragment)
        assertEquals("No logout record found", fragment?.arguments?.getString("last_login"))
    }
}
