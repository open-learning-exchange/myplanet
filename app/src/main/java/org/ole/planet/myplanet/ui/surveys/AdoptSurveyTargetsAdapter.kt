package org.ole.planet.myplanet.ui.surveys

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.ole.planet.myplanet.model.TeamSummary

class AdoptSurveyTargetsAdapter(
    context: Context,
    private val rows: List<Any>
) : ArrayAdapter<Any>(context, android.R.layout.simple_list_item_1, rows) {

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int = if (rows[position] is TeamSummary) TYPE_TARGET else TYPE_TITLE

    override fun areAllItemsEnabled(): Boolean = false

    override fun isEnabled(position: Int): Boolean = rows[position] is TeamSummary

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val isTarget = getItemViewType(position) == TYPE_TARGET
        val view = convertView as? TextView ?: (LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView).apply {
            if (isTarget) {
                setPaddingRelative(paddingStart + (INDENT_DP * resources.displayMetrics.density).toInt(), paddingTop, paddingEnd, paddingBottom)
            } else {
                setTypeface(typeface, Typeface.BOLD)
            }
        }
        view.text = when (val row = rows[position]) {
            is TeamSummary -> row.name
            else -> row.toString()
        }
        return view
    }

    companion object {
        private const val TYPE_TITLE = 0
        private const val TYPE_TARGET = 1
        private const val INDENT_DP = 16

        fun rowsFor(targets: List<TeamSummary>, teamsTitle: String, enterprisesTitle: String): List<Any> {
            val (enterprises, teams) = targets.partition { it.type == "enterprise" }
            return buildList {
                if (teams.isNotEmpty()) { add(teamsTitle); addAll(teams) }
                if (enterprises.isNotEmpty()) { add(enterprisesTitle); addAll(enterprises) }
            }
        }
    }
}
