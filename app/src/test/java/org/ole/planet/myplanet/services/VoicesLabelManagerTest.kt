package org.ole.planet.myplanet.services

import android.content.Context
import android.view.View
import android.widget.Button
import com.google.android.flexbox.FlexboxLayout
import fisk.chipcloud.ChipCloud
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.RealmList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.utils.Constants

class VoicesLabelManagerTest {

    private lateinit var context: Context
    private lateinit var voicesRepository: VoicesRepository
    private lateinit var scope: CoroutineScope
    private lateinit var voicesLabelManager: VoicesLabelManager
    private lateinit var binding: RowNewsBinding
    private lateinit var btnAddLabel: Button
    private lateinit var fbChips: FlexboxLayout
    private lateinit var voice: RealmNews

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        voicesRepository = mockk(relaxed = true)
        scope = TestScope()

        mockkObject(org.ole.planet.myplanet.utils.Utilities)
        every { org.ole.planet.myplanet.utils.Utilities.getCloudConfig() } returns mockk(relaxed = true)

        voicesLabelManager = VoicesLabelManager(context, voicesRepository, scope)

        binding = mockk(relaxed = true)
        mockkConstructor(ChipCloud::class)
        every { anyConstructed<ChipCloud>().addChip(any<String>()) } answers { }
        every { anyConstructed<ChipCloud>().setDeleteListener(any<fisk.chipcloud.ChipDeletedListener>()) } answers { }
        btnAddLabel = mockk(relaxed = true)
        fbChips = mockk(relaxed = true)

        val btnAddLabelField = RowNewsBinding::class.java.getField("btnAddLabel")
        btnAddLabelField.isAccessible = true
        btnAddLabelField.set(binding, btnAddLabel)

        val fbChipsField = RowNewsBinding::class.java.getField("fbChips")
        fbChipsField.isAccessible = true
        fbChipsField.set(binding, fbChips)

        voice = mockk(relaxed = true)
        every { voice.id } returns "test-id"
        every { voice.labels } returns null
    }

    @After
    fun tearDown() {
        clearAllMocks()
        io.mockk.unmockkObject(org.ole.planet.myplanet.utils.Utilities)
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
        verify(exactly = 1) { btnAddLabel.setOnClickListener(any()) }
    }

    @Test
    fun testSetupAddLabelMenu_CanManageLabels() {
        voicesLabelManager.setupAddLabelMenu(binding, voice, true)

        verify { btnAddLabel.isEnabled = true }
        verify(exactly = 2) { btnAddLabel.setOnClickListener(any()) }
    }

    @Test
    fun testShowChips_EmptyLabels_CannotManage() {
        voicesLabelManager.showChips(binding, voice, false)

        verify { fbChips.removeAllViews() }
        verify { btnAddLabel.visibility = View.GONE }
    }

    @Test
    fun testShowChips_WithLabels_CannotManage() {
        val labelsMock = mockk<RealmList<String>>(relaxed = true)
        every { labelsMock.iterator() } answers { mutableListOf("offer").iterator() }
        val labelsSet = setOf("offer")
        every { voice.labels } returns labelsMock

        voicesLabelManager.showChips(binding, voice, false)

        verify { fbChips.removeAllViews() }
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
        val allLabelsMock = mockk<RealmList<String>>(relaxed = true)
        every { allLabelsMock.size } returns Constants.LABELS.values.size
        every { allLabelsMock.iterator() } answers { Constants.LABELS.values.iterator() }
        every { voice.labels } returns allLabelsMock

        voicesLabelManager.showChips(binding, voice, true)

        verify { fbChips.removeAllViews() }
        verify { btnAddLabel.visibility = View.GONE }
    }
}
