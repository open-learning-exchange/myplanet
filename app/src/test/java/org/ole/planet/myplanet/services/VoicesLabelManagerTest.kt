package org.ole.planet.myplanet.services

import android.app.Application
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.chip.Chip
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VoicesLabelManagerTest {

    private lateinit var context: Context
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var scope: TestScope
    private lateinit var voicesLabelManager: VoicesLabelManager
    private lateinit var binding: RowNewsBinding
    private lateinit var btnAddLabel: Button
    private lateinit var fbChips: FlexboxLayout
    private lateinit var voice: News

    private lateinit var addLabelFn: suspend (String, String) -> Unit
    private lateinit var removeLabelFn: suspend (String, String) -> Unit

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val themedContext = android.view.ContextThemeWrapper(app, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar)
        context = themedContext
        dispatcherProvider = mockk(relaxed = true)
        every { dispatcherProvider.main } returns UnconfinedTestDispatcher()
        scope = TestScope()

        addLabelFn = mockk(relaxed = true)
        removeLabelFn = mockk(relaxed = true)

        voicesLabelManager = VoicesLabelManager(
            context = context,
            scope = scope,
            dispatcherProvider = dispatcherProvider,
            addLabelFn = addLabelFn,
            removeLabelFn = removeLabelFn
        )

        binding = RowNewsBinding.inflate(LayoutInflater.from(context))
        btnAddLabel = binding.btnAddLabel
        fbChips = binding.fbChips

        voice = News().apply {
            id = "test-id"
            labels = null
        }
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun testFormatLabelValue() {
        assertEquals("Help Wanted", VoicesLabelManager.formatLabelValue("help_wanted"))
        assertEquals("Request For Advice", VoicesLabelManager.formatLabelValue("request-for-advice"))
        assertEquals("Offer", VoicesLabelManager.formatLabelValue("Offer"))
        assertEquals("Some Random Label", VoicesLabelManager.formatLabelValue("some random label"))
        assertEquals("  ", VoicesLabelManager.formatLabelValue("  ")) // Blank
        assertEquals("Mixed Case Values", VoicesLabelManager.formatLabelValue("MIXED_case-values"))
    }

    @Test
    fun testSetupAddLabelMenu_CannotManageLabels() {
        voicesLabelManager.setupAddLabelMenu(binding, voice, false)

        assertFalse(btnAddLabel.isEnabled)
    }

    @Test
    fun testSetupAddLabelMenu_CanManageLabels() {
        voicesLabelManager.setupAddLabelMenu(binding, voice, true)

        assertTrue(btnAddLabel.isEnabled)
    }

    @Test
    fun testRemoveLabelActionTriggered() = runTest {
        voice.labels = listOf("Offer")

        voicesLabelManager.showChips(binding, voice, true)

        assertEquals(1, fbChips.childCount)
        val chip = fbChips.getChildAt(0) as Chip
        assertEquals("Offer", chip.text)
        chip.performCloseIconClick()
        scope.advanceUntilIdle()

        coVerify(timeout = 1000) { removeLabelFn("test-id", "offer") }
    }

    @Test
    fun testShowChips_EmptyLabels_CannotManage() {
        voicesLabelManager.showChips(binding, voice, false)

        assertEquals(0, fbChips.childCount)
        assertEquals(View.GONE, btnAddLabel.visibility)
    }

    @Test
    fun testShowChips_WithLabels_CannotManage() {
        voice.labels = listOf("offer")

        voicesLabelManager.showChips(binding, voice, false)

        assertEquals(1, fbChips.childCount)
        val chip = fbChips.getChildAt(0) as Chip
        assertEquals("Offer", chip.text)
        assertFalse(chip.isCloseIconVisible)
        assertEquals(View.GONE, btnAddLabel.visibility)
    }

    @Test
    fun testShowChips_EmptyLabels_CanManage() {
        voicesLabelManager.showChips(binding, voice, true)

        assertEquals(0, fbChips.childCount)
        assertEquals(View.VISIBLE, btnAddLabel.visibility)
    }

    @Test
    fun testShowChips_AllLabelsUsed_CanManage() {
        voice.labels = Constants.LABELS.values.toList()

        voicesLabelManager.showChips(binding, voice, true)

        assertEquals(Constants.LABELS.size, fbChips.childCount)
        assertEquals(View.GONE, btnAddLabel.visibility)
    }
}
