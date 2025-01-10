package org.ole.planet.myplanet.utilities

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ListView

class CheckboxListView : ListView, AdapterView.OnItemClickListener {
    var selectedItemsList = ArrayList<Int>()
    var listener: CheckChangeListener? = null

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        onItemClickListener = this
    }

    fun interface CheckChangeListener {
        fun onCheckChange()
    }

    fun setCheckChangeListener(listener: CheckChangeListener?) {
        this.listener = listener
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(context: Context?) : super(context) {
        onItemClickListener = this
    }

    override fun onItemClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
        if (selectedItemsList.contains(i)) {
            selectedItemsList.remove(i)
        } else {
            selectedItemsList.add(i)
        }
        if (listener != null) listener?.onCheckChange()
    }
}
