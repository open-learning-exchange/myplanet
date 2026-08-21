package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R

object CsvTableRenderer {

    fun render(table: TableLayout, preview: CsvPreview, compact: Boolean) {
        val context = table.context
        table.removeAllViews()
        if (preview.rows.isEmpty()) return

        val resources = context.resources
        val cellPaddingHorizontal = resources.getDimensionPixelSize(R.dimen.csv_cell_padding_horizontal)
        val cellPaddingVertical = resources.getDimensionPixelSize(R.dimen.csv_cell_padding_vertical)
        val maxCellWidth = resources.getDimensionPixelSize(
            if (compact) R.dimen.csv_cell_max_width_compact else R.dimen.csv_cell_max_width
        )
        val cellMaxLines = if (compact) COMPACT_CELL_MAX_LINES else FULL_CELL_MAX_LINES
        val textSizePx = resources.getDimension(
            if (compact) R.dimen.csv_cell_text_size_compact else R.dimen.csv_cell_text_size
        )
        val textColor = ContextCompat.getColor(context, R.color.daynight_textColor)

        preview.rows.forEachIndexed { rowIndex, row ->
            val isHeader = rowIndex == 0
            val tableRow = TableRow(context)
            tableRow.layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT, TableLayout.LayoutParams.WRAP_CONTENT
            )
            for (columnIndex in 0 until preview.columnCount) {
                val cell = TextView(context)
                cell.text = row.getOrNull(columnIndex).orEmpty()
                cell.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                cell.setTextColor(textColor)
                cell.typeface = if (isHeader) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                cell.maxLines = cellMaxLines
                cell.ellipsize = TextUtils.TruncateAt.END
                cell.maxWidth = maxCellWidth
                cell.setPadding(cellPaddingHorizontal, cellPaddingVertical, cellPaddingHorizontal, cellPaddingVertical)
                tableRow.addView(cell)
            }
            table.addView(tableRow)
            table.addView(divider(context, resources, isHeader))
        }

        if (preview.hasMoreRows) {
            table.addView(
                truncationNotice(context, preview.rows.size, cellPaddingHorizontal, cellPaddingVertical, textSizePx)
            )
        }
    }

    private fun divider(context: Context, resources: Resources, isHeader: Boolean): View {
        val divider = View(context)
        val height = resources.getDimensionPixelSize(
            if (isHeader) R.dimen.csv_header_divider_height else R.dimen.csv_row_divider_height
        )
        divider.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, height)
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.disable_color))
        return divider
    }

    private fun truncationNotice(
        context: Context,
        rowsShown: Int,
        paddingHorizontal: Int,
        paddingVertical: Int,
        textSizePx: Float
    ): TextView {
        val notice = TextView(context)
        notice.text = context.getString(R.string.csv_showing_first_rows, rowsShown)
        notice.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
        notice.setTextColor(ContextCompat.getColor(context, R.color.hint_color))
        notice.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        notice.layoutParams = TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT
        )
        return notice
    }

    private const val COMPACT_CELL_MAX_LINES = 2
    private const val FULL_CELL_MAX_LINES = 4
}
