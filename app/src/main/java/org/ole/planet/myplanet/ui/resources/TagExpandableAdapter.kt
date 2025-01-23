package org.ole.planet.myplanet.ui.resources

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.CheckBox
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowAdapterNavigationChildBinding
import org.ole.planet.myplanet.databinding.RowAdapterNavigationParentBinding
import org.ole.planet.myplanet.model.RealmTag
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.List

class TagExpandableAdapter(private var tagList: List<RealmTag>, private val childMap: HashMap<String, List<RealmTag>>, private val selectedItemsList: ArrayList<RealmTag>) : BaseExpandableListAdapter() {
    private var clickListener: OnClickTagItem? = null
    private var isSelectMultiple = false

    fun setSelectMultiple(selectMultiple: Boolean) {
        isSelectMultiple = selectMultiple
    }

    override fun getGroupCount(): Int {
        return tagList.size
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        return childMap[tagList[groupPosition].id]?.size ?: 0
    }

    override fun getGroup(groupPosition: Int): Any {
        return tagList[groupPosition]
    }

    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        return childMap[tagList[groupPosition].id]?.get(childPosition)
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

    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?): View {
        val headerTitle = tagList[groupPosition].name
        val binding: RowAdapterNavigationParentBinding
        val view: View

        if (convertView == null) {
            binding = RowAdapterNavigationParentBinding.inflate(LayoutInflater.from(parent?.context), parent, false)
            view = binding.root
            view.tag = binding
        } else {
            binding = convertView.tag as RowAdapterNavigationParentBinding
            view = convertView
        }

        binding.tvDrawerTitle1.text = headerTitle
        createCheckbox(view, tagList[groupPosition])
        binding.tvDrawerTitle.text = headerTitle

        if (!childMap.containsKey(tagList[groupPosition].id)) {
            binding.tvDrawerTitle1.visibility = View.VISIBLE
            binding.tvDrawerTitle.visibility = View.GONE
            binding.ivIndicators.visibility = View.GONE
            binding.tvDrawerTitle1.setOnClickListener { clickListener?.onTagClicked(tagList[groupPosition]) }
        } else {
            binding.tvDrawerTitle.visibility = View.VISIBLE
            binding.tvDrawerTitle1.setOnClickListener(null)
            binding.tvDrawerTitle1.visibility = View.GONE
            binding.ivIndicators.visibility = View.VISIBLE
            setExpandedIcon(isExpanded, binding.ivIndicators)
            binding.tvDrawerTitle.setOnClickListener { clickListener?.onTagClicked(tagList[groupPosition]) }
        }

        return view
    }

    private fun setExpandedIcon(isExpanded: Boolean, ivIndicator: AppCompatImageView) {
        ivIndicator.setImageResource(if (isExpanded) R.drawable.ic_keyboard_arrow_up_black_24dp else R.drawable.ic_keyboard_arrow_down_black_24dp)
    }

    private fun createCheckbox(convertView: View, tag: RealmTag) {
        val checkBox = convertView.findViewById<CheckBox>(R.id.checkbox)
        checkBox.visibility = if (isSelectMultiple) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedItemsList.contains(tag)
        checkBox.setOnCheckedChangeListener { _, _ -> clickListener?.onCheckboxTagSelected(tag) }
    }

    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup?): View {
        val tag = getChild(groupPosition, childPosition) as RealmTag
        val binding: RowAdapterNavigationChildBinding
        val view: View

        if (convertView == null) {
            binding = RowAdapterNavigationChildBinding.inflate(LayoutInflater.from(parent?.context), parent, false)
            view = binding.root
            view.tag = binding
        } else {
            binding = convertView.tag as RowAdapterNavigationChildBinding
            view = convertView
        }

        createCheckbox(view, tag)
        binding.tvDrawerTitle.text = tag.name
        binding.root.setBackgroundColor(ContextCompat.getColor(parent?.context!!, R.color.multi_select_grey))
        binding.tvDrawerTitle.setTextColor(ContextCompat.getColor(parent.context, R.color.daynight_textColor))
        binding.tvDrawerTitle.setOnClickListener { clickListener?.onTagClicked(tag) }

        return view
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return false
    }

    fun setClickListener(clickListener: OnClickTagItem) {
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