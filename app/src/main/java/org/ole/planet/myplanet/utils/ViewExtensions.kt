package org.ole.planet.myplanet.utils

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.R
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

fun EditText.textChanges(): Flow<CharSequence?> {
    return callbackFlow {
        val listener = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                trySend(s?.toString())
            }
        }
        addTextChangedListener(listener)
        awaitClose { removeTextChangedListener(listener) }
    }.onStart { emit(text?.toString()) }
}

fun Spinner.setupHintSpinner(hint: String, entries: Array<String>) {
    val items = listOf(hint) + entries.toList()
    val hintColor = ContextCompat.getColor(context, R.color.hint_color)
    val textColor = ContextCompat.getColor(context, R.color.daynight_textColor)
    val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
        override fun isEnabled(position: Int) = position != 0
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent) as TextView
            view.setTextColor(if (position == 0) hintColor else textColor)
            return view
        }
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.isSingleLine = false
            view.maxLines = 2
            return view
        }
    }
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    this.adapter = adapter
    this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
            (view as? TextView)?.setTextColor(if (position == 0) hintColor else textColor)
        }

        override fun onNothingSelected(parent: AdapterView<*>) {}
    }
}
