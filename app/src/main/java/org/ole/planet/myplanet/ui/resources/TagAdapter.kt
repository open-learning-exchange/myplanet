package org.ole.planet.myplanet.ui.resources

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowAdapterNavigationChildBinding
import org.ole.planet.myplanet.databinding.RowAdapterNavigationParentBinding
import org.ole.planet.myplanet.model.RealmTag

class TagAdapter(
    private val selectedItemsList: ArrayList<RealmTag>,
    private val listener: OnTagClickListener
) : ListAdapter<TagData, RecyclerView.ViewHolder>(TagDiffCallback()) {

    private var isSelectMultiple = false

    fun setSelectMultiple(selectMultiple: Boolean) {
        isSelectMultiple = selectMultiple
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TagData.Parent -> R.layout.row_adapter_navigation_parent
            is TagData.Child -> R.layout.row_adapter_navigation_child
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.row_adapter_navigation_parent -> {
                val binding = RowAdapterNavigationParentBinding.inflate(inflater, parent, false)
                ParentViewHolder(binding)
            }
            R.layout.row_adapter_navigation_child -> {
                val binding = RowAdapterNavigationChildBinding.inflate(inflater, parent, false)
                ChildViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TagData.Parent -> (holder as ParentViewHolder).bind(item)
            is TagData.Child -> (holder as ChildViewHolder).bind(item)
        }
    }

    inner class ParentViewHolder(private val binding: RowAdapterNavigationParentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(parent: TagData.Parent) {
            binding.tvDrawerTitle1.text = parent.tag.name
            binding.tvDrawerTitle.text = parent.tag.name

            val hasChildren = listener.hasChildren(parent.tag.id)

            if (!hasChildren) {
                binding.tvDrawerTitle1.visibility = View.VISIBLE
                binding.tvDrawerTitle.visibility = View.GONE
                binding.ivIndicators.visibility = View.GONE
                binding.tvDrawerTitle1.setOnClickListener { listener.onTagClicked(parent.tag) }
            } else {
                binding.tvDrawerTitle.visibility = View.VISIBLE
                binding.tvDrawerTitle1.visibility = View.GONE
                binding.ivIndicators.visibility = View.VISIBLE
                setExpandedIcon(parent.isExpanded, binding.ivIndicators)
                binding.root.setOnClickListener { listener.onParentTagClicked(parent) }
            }
            createCheckbox(binding.root, parent.tag)
        }
    }

    inner class ChildViewHolder(private val binding: RowAdapterNavigationChildBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(child: TagData.Child) {
            binding.tvDrawerTitle.text = child.tag.name
            binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.multi_select_grey))
            binding.tvDrawerTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.daynight_textColor))
            binding.tvDrawerTitle.setOnClickListener { listener.onTagClicked(child.tag) }
            createCheckbox(binding.root, child.tag)
        }
    }

    private fun setExpandedIcon(isExpanded: Boolean, ivIndicator: AppCompatImageView) {
        ivIndicator.setImageResource(if (isExpanded) R.drawable.ic_keyboard_arrow_up_black_24dp else R.drawable.ic_keyboard_arrow_down_black_24dp)
    }

    private fun createCheckbox(convertView: View, tag: RealmTag) {
        val checkBox = convertView.findViewById<CheckBox>(R.id.checkbox)
        checkBox.visibility = if (isSelectMultiple) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedItemsList.contains(tag)
        checkBox.setOnCheckedChangeListener { _, _ -> listener.onCheckboxTagSelected(tag) }
    }

    interface OnTagClickListener {
        fun onTagClicked(tag: RealmTag)
        fun onParentTagClicked(parent: TagData.Parent)
        fun onCheckboxTagSelected(tags: RealmTag)
        fun hasChildren(tagId: String?): Boolean
    }
}

class TagDiffCallback : DiffUtil.ItemCallback<TagData>() {
    override fun areItemsTheSame(oldItem: TagData, newItem: TagData): Boolean {
        return when {
            oldItem is TagData.Parent && newItem is TagData.Parent -> oldItem.tag.id == newItem.tag.id
            oldItem is TagData.Child && newItem is TagData.Child -> oldItem.tag.id == newItem.tag.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: TagData, newItem: TagData): Boolean {
        return when {
            oldItem is TagData.Parent && newItem is TagData.Parent -> oldItem.tag.name == newItem.tag.name && oldItem.isExpanded == newItem.isExpanded
            oldItem is TagData.Child && newItem is TagData.Child -> oldItem.tag.name == newItem.tag.name
            else -> false
        }
    }
}
