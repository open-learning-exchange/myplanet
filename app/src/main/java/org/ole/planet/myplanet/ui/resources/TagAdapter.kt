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
    private val listener: OnClickTagItem
) : ListAdapter<TagListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    private var isSelectMultiple = false

    fun setSelectMultiple(selectMultiple: Boolean) {
        isSelectMultiple = selectMultiple
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TagListItem.Parent -> R.layout.row_adapter_navigation_parent
            is TagListItem.Child -> R.layout.row_adapter_navigation_child
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
            is TagListItem.Parent -> (holder as ParentViewHolder).bind(item)
            is TagListItem.Child -> (holder as ChildViewHolder).bind(item)
        }
    }

    inner class ParentViewHolder(private val binding: RowAdapterNavigationParentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TagListItem.Parent) {
            binding.tvDrawerTitle1.text = item.tag.name
            binding.tvDrawerTitle.text = item.tag.name

            if (!item.hasChildren) {
                binding.tvDrawerTitle1.visibility = View.VISIBLE
                binding.tvDrawerTitle.visibility = View.GONE
                binding.ivIndicators.visibility = View.GONE
                binding.tvDrawerTitle1.setOnClickListener { listener.onTagClicked(item.tag) }
            } else {
                binding.tvDrawerTitle.visibility = View.VISIBLE
                binding.tvDrawerTitle1.visibility = View.GONE
                binding.ivIndicators.visibility = View.VISIBLE
                setExpandedIcon(item.isExpanded, binding.ivIndicators)
                binding.root.setOnClickListener {
                    listener.onParentTagClicked(item, bindingAdapterPosition)
                }
            }
            createCheckbox(binding.root, item.tag)
        }
    }

    inner class ChildViewHolder(private val binding: RowAdapterNavigationChildBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TagListItem.Child) {
            binding.tvDrawerTitle.text = item.tag.name
            binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.multi_select_grey))
            binding.tvDrawerTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.daynight_textColor))
            binding.tvDrawerTitle.setOnClickListener { listener.onTagClicked(item.tag) }
            createCheckbox(binding.root, item.tag)
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

    class DiffCallback : DiffUtil.ItemCallback<TagListItem>() {
        override fun areItemsTheSame(oldItem: TagListItem, newItem: TagListItem): Boolean {
            return when {
                oldItem is TagListItem.Parent && newItem is TagListItem.Parent -> oldItem.tag.id == newItem.tag.id
                oldItem is TagListItem.Child && newItem is TagListItem.Child -> oldItem.tag.id == newItem.tag.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TagListItem, newItem: TagListItem): Boolean {
            return when {
                oldItem is TagListItem.Parent && newItem is TagListItem.Parent -> oldItem.tag == newItem.tag && oldItem.isExpanded == newItem.isExpanded
                oldItem is TagListItem.Child && newItem is TagListItem.Child -> oldItem.tag == newItem.tag
                else -> false
            }
        }
    }

    interface OnClickTagItem {
        fun onTagClicked(tag: RealmTag)
        fun onCheckboxTagSelected(tag: RealmTag)
        fun onParentTagClicked(parent: TagListItem.Parent, position: Int)
    }
}
