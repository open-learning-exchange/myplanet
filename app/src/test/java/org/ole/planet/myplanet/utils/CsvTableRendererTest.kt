package org.ole.planet.myplanet.utils

import android.graphics.Typeface
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CsvTableRendererTest {

    private lateinit var table: TableLayout

    @Before
    fun setup() {
        table = TableLayout(ApplicationProvider.getApplicationContext())
    }

    private fun rows(): List<TableRow> = (0 until table.childCount)
        .mapNotNull { table.getChildAt(it) as? TableRow }

    @Test
    fun `render lays out one row per csv row with a cell per column`() {
        val preview = CsvPreview(
            rows = listOf(listOf("Title", "Steps"), listOf("Cyber sec", "1")),
            hasMoreRows = false
        )

        CsvTableRenderer.render(table, preview, compact = true)

        val rows = rows()
        assertEquals(2, rows.size)
        assertEquals(2, rows[0].childCount)
        assertEquals("Title", (rows[0].getChildAt(0) as TextView).text.toString())
        assertEquals("1", (rows[1].getChildAt(1) as TextView).text.toString())
    }

    @Test
    fun `render pads short rows so columns stay aligned`() {
        val preview = CsvPreview(
            rows = listOf(listOf("A", "B", "C"), listOf("only")),
            hasMoreRows = false
        )

        CsvTableRenderer.render(table, preview, compact = true)

        val rows = rows()
        assertEquals(3, rows[1].childCount)
        assertEquals("", (rows[1].getChildAt(2) as TextView).text.toString())
    }

    @Test
    fun `render emphasises the header row only`() {
        val preview = CsvPreview(
            rows = listOf(listOf("Title"), listOf("Cyber sec")),
            hasMoreRows = false
        )

        CsvTableRenderer.render(table, preview, compact = true)

        val rows = rows()
        assertSame(Typeface.DEFAULT_BOLD, (rows[0].getChildAt(0) as TextView).typeface)
        assertSame(Typeface.DEFAULT, (rows[1].getChildAt(0) as TextView).typeface)
    }

    @Test
    fun `render uses a bigger cell text size when not compact`() {
        val preview = CsvPreview(rows = listOf(listOf("Title")), hasMoreRows = false)
        val resources = table.context.resources

        CsvTableRenderer.render(table, preview, compact = true)
        val compactSize = (rows()[0].getChildAt(0) as TextView).textSize

        CsvTableRenderer.render(table, preview, compact = false)
        val fullSize = (rows()[0].getChildAt(0) as TextView).textSize

        assertEquals(resources.getDimension(R.dimen.csv_cell_text_size_compact), compactSize, 0.1f)
        assertEquals(resources.getDimension(R.dimen.csv_cell_text_size), fullSize, 0.1f)
        assertTrue(fullSize > compactSize)
    }

    @Test
    fun `render appends a truncation notice when more rows exist`() {
        val preview = CsvPreview(rows = listOf(listOf("Title"), listOf("Cyber sec")), hasMoreRows = true)

        CsvTableRenderer.render(table, preview, compact = true)

        val notice = (0 until table.childCount)
            .mapNotNull { table.getChildAt(it) as? TextView }
            .lastOrNull()
        assertEquals(
            table.context.getString(R.string.csv_showing_first_rows, 2),
            notice?.text?.toString()
        )
    }

    @Test
    fun `render replaces the previous table content`() {
        CsvTableRenderer.render(
            table,
            CsvPreview(rows = listOf(listOf("old")), hasMoreRows = false),
            compact = true
        )
        CsvTableRenderer.render(
            table,
            CsvPreview(rows = listOf(listOf("new")), hasMoreRows = false),
            compact = true
        )

        val rows = rows()
        assertEquals(1, rows.size)
        assertEquals("new", (rows[0].getChildAt(0) as TextView).text.toString())
    }

    @Test
    fun `render leaves the table empty for an empty preview`() {
        CsvTableRenderer.render(table, CsvPreview(rows = emptyList(), hasMoreRows = false), compact = true)

        assertEquals(0, table.childCount)
    }
}
