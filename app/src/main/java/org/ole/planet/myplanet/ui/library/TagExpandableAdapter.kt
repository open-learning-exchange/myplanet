package org.ole.planet.myplanet.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.widget.AppCompatImageView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowAdapterNavigationChildBinding
import org.ole.planet.myplanet.databinding.RowAdapterNavigationParentBinding
import org.ole.planet.myplanet.model.RealmTag

class TagExpandableAdapter(private var tagList: List<RealmTag>, private val childMap: HashMap<String?, MutableList<RealmTag>>, selectedItemsList: MutableList<RealmTag>) : BaseExpandableListAdapter() {
    private var clickListener: OnClickTagItem? = null
    private var isSelectMultiple = false
    private var selectedItemsList = ArrayList<RealmTag>()

    init {
        this.selectedItemsList = selectedItemsList as ArrayList<RealmTag>
    }

    fun setSelectMultiple(selectMultiple: Boolean) {
        isSelectMultiple = selectMultiple
    }

    override fun getGroupCount(): Int {
        return tagList.size
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        return if (childMap.containsKey(tagList[groupPosition].id)) {
            childMap[tagList[groupPosition].id]!!.size
        } else 0
    }

    override fun getGroup(groupPosition: Int): Any {
        return tagList[groupPosition]
    }

    override fun getChild(groupPosition: Int, childPosition: Int): RealmTag? {
        return if (childMap.containsKey(tagList[groupPosition].id)) {
            childMap[tagList[groupPosition].id]!![childPosition]
        } else null
    }

    override fun getGroupId(groupPosition: Int): Long {
        return groupPosition.toLong()
    }

    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        return childPosition.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View, parent: ViewGroup): View {
        var convertView: View? = convertView
        val headerTitle = tagList[groupPosition].name
        val rowAdapterNavigationParentBinding: RowAdapterNavigationParentBinding
        if (convertView == null) {
            rowAdapterNavigationParentBinding = RowAdapterNavigationParentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            convertView = rowAdapterNavigationParentBinding.root
            convertView.setTag(rowAdapterNavigationParentBinding)
        } else {
            rowAdapterNavigationParentBinding = convertView.tag as RowAdapterNavigationParentBinding
        }
        rowAdapterNavigationParentBinding.tvDrawerTitle1.text = headerTitle
        createCheckbox(convertView, tagList[groupPosition])
        rowAdapterNavigationParentBinding.tvDrawerTitle.text = headerTitle
        if (!childMap.containsKey(tagList[groupPosition].id)) {
            rowAdapterNavigationParentBinding.tvDrawerTitle1.visibility = View.VISIBLE
            rowAdapterNavigationParentBinding.tvDrawerTitle.visibility = View.GONE
            rowAdapterNavigationParentBinding.ivIndicators.visibility = View.GONE
            rowAdapterNavigationParentBinding.tvDrawerTitle1.setOnClickListener {
                clickListener!!.onTagClicked(tagList[groupPosition])
            }
        } else {
            rowAdapterNavigationParentBinding.tvDrawerTitle.visibility = View.VISIBLE
            rowAdapterNavigationParentBinding.tvDrawerTitle1.setOnClickListener(null)
            rowAdapterNavigationParentBinding.tvDrawerTitle1.visibility = View.GONE
            rowAdapterNavigationParentBinding.ivIndicators.visibility = View.VISIBLE
            setExpandedIcon(isExpanded, rowAdapterNavigationParentBinding.ivIndicators)
            rowAdapterNavigationParentBinding.tvDrawerTitle.setOnClickListener {
                clickListener!!.onTagClicked(tagList[groupPosition])
            }
        }
        return convertView
    }

    private fun setExpandedIcon(isExpanded: Boolean, ivIndicator: AppCompatImageView) {
        if (isExpanded) {
            ivIndicator.setImageResource(R.drawable.ic_keyboard_arrow_up_black_24dp)
        } else {
            ivIndicator.setImageResource(R.drawable.ic_keyboard_arrow_down_black_24dp)
        }
    }

    private fun createCheckbox(convertView: View, tag: RealmTag) {
        val checkBox = convertView.findViewById<CheckBox>(R.id.checkbox)
        checkBox.visibility = if (isSelectMultiple) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedItemsList.contains(tag)
        checkBox.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean ->
            clickListener!!.onCheckboxTagSelected(tag)
        }
    }

    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View, parent: ViewGroup): View {
        var convertView: View? = convertView
        val tag = getChild(groupPosition, childPosition) as RealmTag
        val rowAdapterNavigationChildBinding: RowAdapterNavigationChildBinding
        if (convertView == null) {
            rowAdapterNavigationChildBinding = RowAdapterNavigationChildBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            convertView = rowAdapterNavigationChildBinding.root
            convertView.setTag(rowAdapterNavigationChildBinding)
        } else {
            rowAdapterNavigationChildBinding = convertView.tag as RowAdapterNavigationChildBinding
        }
        createCheckbox(convertView, tag)
        rowAdapterNavigationChildBinding.tvDrawerTitle.text = tag.name
        rowAdapterNavigationChildBinding.tvDrawerTitle.setOnClickListener {
            if (clickListener != null) {
                clickListener!!.onTagClicked(tag)
            }
        }
        return convertView
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return false
    }

    fun setClickListener(clickListener: OnClickTagItem?) {
        this.clickListener = clickListener
    }

    fun setTagList(filteredList: List<RealmTag>) {
        tagList = filteredList
        notifyDataSetChanged()
    }

    interface OnClickTagItem {
        fun onTagClicked(tag: RealmTag)
        fun onCheckboxTagSelected(tags: RealmTag)
    }
}
