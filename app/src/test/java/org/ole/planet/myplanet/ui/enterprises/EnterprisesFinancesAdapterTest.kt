package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.databinding.RowFinanceBinding
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.utils.FileUtils
import java.io.File

@RunWith(AndroidJUnit4::class)
class EnterprisesFinancesAdapterTest {

    private lateinit var adapter: EnterprisesFinancesAdapter
    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(AppCompatR.style.Theme_AppCompat)
        tempDir = File(context.cacheDir, "test_ole_${System.currentTimeMillis()}").apply { mkdirs() }
        mockkObject(FileUtils)
        every { FileUtils.getOlePath(any()) } returns "${tempDir.absolutePath}/"
        adapter = EnterprisesFinancesAdapter(context)
    }

    @After
    fun tearDown() {
        unmockkObject(FileUtils)
        tempDir.deleteRecursively()
    }

    @Test
    fun testBindFinanceImage_missingFile_visibilityGone() {
        val transaction = Transaction(
            id = "tx1",
            date = System.currentTimeMillis(),
            description = "Test transaction",
            type = "debit",
            amount = 100,
            balance = 500,
            imageName = "missing.jpg"
        )

        adapter.submitList(listOf(transaction)) {
            val binding = RowFinanceBinding.inflate(LayoutInflater.from(context))
            val viewHolder = EnterprisesFinancesAdapter.FinanceViewHolder(binding)

            adapter.onBindViewHolder(viewHolder, 0)

            assertEquals(View.GONE, viewHolder.binding.financeImage.visibility)
        }
    }

    @Test
    fun testBindFinanceImage_existingFile_cachesAndInvalidatesOnListChange() {
        val teamAttachmentsDir = File(tempDir, "team_attachments/tx1").apply { mkdirs() }
        val imageFile = File(teamAttachmentsDir, "receipt.jpg")
        imageFile.createNewFile()

        val transaction = Transaction(
            id = "tx1",
            date = System.currentTimeMillis(),
            description = "Test transaction",
            type = "debit",
            amount = 100,
            balance = 500,
            imageName = "receipt.jpg"
        )

        adapter.submitList(listOf(transaction)) {
            val binding = RowFinanceBinding.inflate(LayoutInflater.from(context))
            val viewHolder = EnterprisesFinancesAdapter.FinanceViewHolder(binding)

            // First bind detects existing file and makes image visible
            adapter.onBindViewHolder(viewHolder, 0)
            assertEquals(View.VISIBLE, viewHolder.binding.financeImage.visibility)

            // File is deleted on disk
            imageFile.delete()

            // Second bind within TTL uses cached exists value (true) so image remains visible
            adapter.onBindViewHolder(viewHolder, 0)
            assertEquals(View.VISIBLE, viewHolder.binding.financeImage.visibility)

            // Re-submitting current list invalidates cache via onCurrentListChanged
            adapter.submitList(listOf(transaction)) {
                adapter.onBindViewHolder(viewHolder, 0)
                // Cache was cleared, so disk re-check detects file is deleted and sets visibility to GONE
                assertEquals(View.GONE, viewHolder.binding.financeImage.visibility)
            }
        }
    }

    @Test
    fun testBindFinanceImage_cacheExpiresAfterTtl() {
        val teamAttachmentsDir = File(tempDir, "team_attachments/tx1").apply { mkdirs() }
        val imageFile = File(teamAttachmentsDir, "receipt.jpg")

        val transaction = Transaction(
            id = "tx1",
            date = System.currentTimeMillis(),
            description = "Test transaction",
            type = "debit",
            amount = 100,
            balance = 500,
            imageName = "receipt.jpg"
        )

        adapter.submitList(listOf(transaction)) {
            val binding = RowFinanceBinding.inflate(LayoutInflater.from(context))
            val viewHolder = EnterprisesFinancesAdapter.FinanceViewHolder(binding)

            // File doesn't exist initially -> GONE (and cached false)
            adapter.onBindViewHolder(viewHolder, 0)
            assertEquals(View.GONE, viewHolder.binding.financeImage.visibility)

            // File appears on disk (e.g. downloaded)
            imageFile.createNewFile()

            // Immediate re-bind within TTL uses cached false -> still GONE
            adapter.onBindViewHolder(viewHolder, 0)
            assertEquals(View.GONE, viewHolder.binding.financeImage.visibility)

            // Sleep past the 5000ms TTL
            Thread.sleep(5100L)

            // Re-bind after TTL expires re-stats disk -> VISIBLE
            adapter.onBindViewHolder(viewHolder, 0)
            assertEquals(View.VISIBLE, viewHolder.binding.financeImage.visibility)
        }
    }
}
