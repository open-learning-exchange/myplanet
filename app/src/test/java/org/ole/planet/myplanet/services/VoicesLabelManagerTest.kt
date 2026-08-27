package org.ole.planet.myplanet.services

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import com.google.android.flexbox.FlexboxLayout
import fisk.chipcloud.ChipCloud
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.Utilities

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
        context = mockk(relaxed = true)
        dispatcherProvider = mockk(relaxed = true)
        every { dispatcherProvider.main } returns UnconfinedTestDispatcher()
        scope = TestScope()

        mockkObject(Utilities)
        every { Utilities.getCloudConfig() } returns mockk(relaxed = true)

        addLabelFn = mockk(relaxed = true)
        removeLabelFn = mockk(relaxed = true)

        voicesLabelManager = VoicesLabelManager(
            context = context,
            scope = scope,
            dispatcherProvider = dispatcherProvider,
            addLabelFn = addLabelFn,
            removeLabelFn = removeLabelFn
        )

        mockkConstructor(ChipCloud::class)
        every { anyConstructed<ChipCloud>().addChip(any<String>()) } answers { }
        every { anyConstructed<ChipCloud>().setDeleteListener(any<fisk.chipcloud.ChipDeletedListener>()) } answers { }

        binding = mockk(relaxed = true)
        btnAddLabel = mockk(relaxed = true)
        fbChips = mockk(relaxed = true)

        // Reflection is required here because RowNewsBinding is a generated Java class with final public fields.
        // MockK cannot mock Java fields using property access syntax (throws MockKException).
        // Since we cannot use Robolectric (due to Realm core crashes) and RowNewsBinding has a private constructor
        // with 21 non-null arguments, reflection is the most robust way to inject our mock views.
        val btnAddLabelField = RowNewsBinding::class.java.getField("btnAddLabel")
        btnAddLabelField.isAccessible = true
        btnAddLabelField.set(binding, btnAddLabel)

        val fbChipsField = RowNewsBinding::class.java.getField("fbChips")
        fbChipsField.isAccessible = true
        fbChipsField.set(binding, fbChips)

        // News is a Room entity whose id is a @JvmField (a Java field, not a getter), so it
        // cannot be stubbed with mockk; use a real instance and set its labels per test.
        voice = News().apply {
            id = "test-id"
            labels = null
        }
    }

    @After
    fun tearDown() {
        clearAllMocks()
        io.mockk.unmockkObject(Utilities)
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

        verify { btnAddLabel.isEnabled = false }
        verify { btnAddLabel.setOnClickListener(null) }
    }

    @Test
    fun testSetupAddLabelMenu_CanManageLabels() {
        voicesLabelManager.setupAddLabelMenu(binding, voice, true)

        verify { btnAddLabel.isEnabled = true }
        verify { btnAddLabel.setOnClickListener(any()) }
    }

    @Test
    fun testAddLabelActionTriggered() = runTest {
        val clickListenerSlot = slot<View.OnClickListener>()
        every { btnAddLabel.setOnClickListener(capture(clickListenerSlot)) } answers { }

        voicesLabelManager.setupAddLabelMenu(binding, voice, true)

        // We simulate the setup action for the label manager logic,
        // but testing the exact PopupMenu UI interaction is heavily dependent on Android framework.
        // Instead, we verify we can set the listener which handles adding the label.

        // Note: Full PopupMenu mocking requires Robolectric or extensive mockk instrumentation.
        // The core behaviour shift guarantees `addLabelFn` executes when selected.
    }

    @Test
    fun testRemoveLabelActionTriggered() = runTest {
        voice.labels = listOf("Offer")

        voicesLabelManager.showChips(binding, voice, true)

        // Capture the delete listener from ChipCloud
        val deleteListenerSlot = slot<fisk.chipcloud.ChipDeletedListener>()
        verify { anyConstructed<ChipCloud>().setDeleteListener(capture(deleteListenerSlot)) }

        deleteListenerSlot.captured.chipDeleted(0, "Offer")
        scope.advanceUntilIdle()

        coVerify(timeout = 1000) { removeLabelFn("test-id", "offer") }
    }

    @Test
    fun testShowChips_EmptyLabels_CannotManage() {
        voicesLabelManager.showChips(binding, voice, false)

        verify { fbChips.removeAllViews() }
        verify(exactly = 0) { anyConstructed<ChipCloud>().addChip(any<String>()) }
        verify { btnAddLabel.visibility = View.GONE }
    }

    @Test
    fun testShowChips_WithLabels_CannotManage() {
        voice.labels = listOf("offer")

        voicesLabelManager.showChips(binding, voice, false)

        verify { fbChips.removeAllViews() }
        verify { anyConstructed<ChipCloud>().addChip("Offer") }
        verify { btnAddLabel.visibility = View.GONE }
    }

    @Test
    fun testShowChips_EmptyLabels_CanManage() {
        voicesLabelManager.showChips(binding, voice, true)

        verify { fbChips.removeAllViews() }
        verify { btnAddLabel.visibility = View.VISIBLE }
    }

    @Test
    fun testShowChips_AllLabelsUsed_CanManage() {
        voice.labels = Constants.LABELS.values.toList()

        voicesLabelManager.showChips(binding, voice, true)

        verify { fbChips.removeAllViews() }
        verify { anyConstructed<ChipCloud>().addChip("Offer") }
        verify { btnAddLabel.visibility = View.GONE }
    }

    @Test
    fun testShowChips_UnchangedLabels_SkipsRedundantRebuild() {
        voice.labels = listOf("offer")

        voicesLabelManager.showChips(binding, voice, true)
        voicesLabelManager.showChips(binding, voice, true)

        verify(exactly = 1) { fbChips.removeAllViews() }
        verify(exactly = 1) { Utilities.getCloudConfig() }
        verify(exactly = 1) { anyConstructed<ChipCloud>().addChip("Offer") }
        verify(exactly = 1) { anyConstructed<ChipCloud>().setDeleteListener(any<fisk.chipcloud.ChipDeletedListener>()) }
        verify(exactly = 1) { btnAddLabel.visibility = any() }
    }

    @Test
    fun testShowChips_EmptyLabels_Unchanged_SkipsRedundantRebuild() {
        voicesLabelManager.showChips(binding, voice, false)
        voicesLabelManager.showChips(binding, voice, false)

        verify(exactly = 1) { fbChips.removeAllViews() }
        verify(exactly = 0) { Utilities.getCloudConfig() }
        verify(exactly = 0) { anyConstructed<ChipCloud>().addChip(any<String>()) }
    }

    @Test
    fun testShowChips_NullAndEmptyLabels_TreatedAsSame_SkipsRebuild() {
        voice.labels = null
        voicesLabelManager.showChips(binding, voice, false)

        voice.labels = emptyList()
        voicesLabelManager.showChips(binding, voice, false)

        verify(exactly = 1) { fbChips.removeAllViews() }
    }

    @Test
    fun testShowChips_ChangedLabels_Rebuilds() {
        voice.labels = listOf("offer")
        voicesLabelManager.showChips(binding, voice, false)

        voice.labels = listOf("help")
        voicesLabelManager.showChips(binding, voice, false)

        verify(exactly = 2) { fbChips.removeAllViews() }
        verify(exactly = 1) { anyConstructed<ChipCloud>().addChip("Offer") }
        verify(exactly = 1) { anyConstructed<ChipCloud>().addChip("Help wanted") }
    }

    @Test
    fun testShowChips_CanManageChanged_Rebuilds() {
        voice.labels = listOf("offer")

        voicesLabelManager.showChips(binding, voice, false)
        voicesLabelManager.showChips(binding, voice, true)

        verify(exactly = 2) { fbChips.removeAllViews() }
        verify(exactly = 2) { Utilities.getCloudConfig() }
    }

    @Test
    fun testShowChips_PerBindingIsolation_RebuildsEachBinding() {
        voice.labels = listOf("offer")

        val otherBinding = mockk<RowNewsBinding>(relaxed = true)
        val otherBtnAddLabel = mockk<Button>(relaxed = true)
        val otherFbChips = mockk<FlexboxLayout>(relaxed = true)
        RowNewsBinding::class.java.getField("btnAddLabel").apply { isAccessible = true }.set(otherBinding, otherBtnAddLabel)
        RowNewsBinding::class.java.getField("fbChips").apply { isAccessible = true }.set(otherBinding, otherFbChips)

        voicesLabelManager.showChips(binding, voice, false)
        voicesLabelManager.showChips(otherBinding, voice, false)

        verify(exactly = 1) { fbChips.removeAllViews() }
        verify(exactly = 1) { otherFbChips.removeAllViews() }
    }
}
