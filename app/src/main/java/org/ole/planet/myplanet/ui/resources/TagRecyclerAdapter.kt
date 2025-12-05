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

sealed class TagAdapterItem {
    abstract val id: String
    data class Parent(val tag: RealmTag, val children: List<Child>, var isExpanded: Boolean = false) : TagAdapterItem() {
        override val id: String = tag.id ?: ""
    }
    data class Child(val tag: RealmTag) : TagAdapterItem() {
        override val id: String = tag.id ?: ""
    }
}

class TagRecyclerAdapter(
    private val listener: OnClickTagItem
) : ListAdapter<TagAdapterItem, RecyclerView.ViewHolder>(TAG_DIFF_CALLBACK) {

    private var isSelectMultiple = false
    private var selectedItemsList = ArrayList<RealmTag>()

    fun setSelectMultiple(selectMultiple: Boolean) {
        isSelectMultiple = selectMultiple
        notifyItemRangeChanged(0, itemCount)
    }

    fun setSelectedItems(selected: List<RealmTag>) {
        selectedItemsList.clear()
        selectedItemsList.addAll(selected)
        notifyItemRangeChanged(0, itemCount)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TagAdapterItem.Parent -> R.layout.row_adapter_navigation_parent
            is TagAdapterItem.Child -> R.layout.row_adapter_navigation_child
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
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TagAdapterItem.Parent -> (holder as ParentViewHolder).bind(item)
            is TagAdapterItem.Child -> (holder as ChildViewHolder).bind(item)
        }
    }

    inner class ParentViewHolder(private val binding: RowAdapterNavigationParentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TagAdapterItem.Parent) {
            binding.tvDrawerTitle1.text = item.tag.name
            binding.tvDrawerTitle.text = item.tag.name
            createCheckbox(binding.root, item.tag)

            if (item.children.isEmpty()) {
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
                    listener.onParentTagClicked(item)
                }
                binding.tvDrawerTitle.setOnClickListener { listener.onTagClicked(item.tag) }
            }
        }
    }

    inner class ChildViewHolder(private val binding: RowAdapterNavigationChildBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TagAdapterItem.Child) {
            binding.tvDrawerTitle.text = item.tag.name
            createCheckbox(binding.root, item.tag)
            binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.multi_select_grey))
            binding.tvDrawerTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.daynight_textColor))
            binding.tvDrawerTitle.setOnClickListener { listener.onTagClicked(item.tag) }
        }
    }

    private fun createCheckbox(convertView: View, tag: RealmTag) {
        val checkBox = convertView.findViewById<CheckBox>(R.id.checkbox)
        checkBox.visibility = if (isSelectMultiple) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedItemsList.any { it.id == tag.id }
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            listener.onCheckboxTagSelected(tag, isChecked)
        }
    }

    private fun setExpandedIcon(isExpanded: Boolean, ivIndicator: AppCompatImageView) {
        ivIndicator.setImageResource(if (isExpanded) R.drawable.ic_keyboard_arrow_up_black_24dp else R.drawable.ic_keyboard_arrow_down_black_24dp)
    }

    interface OnClickTagItem {
        fun onTagClicked(tag: RealmTag)
        fun onCheckboxTagSelected(tag: RealmTag, isChecked: Boolean)
        fun onParentTagClicked(parent: TagAdapterItem.Parent)
    }

    companion object {
        private val TAG_DIFF_CALLBACK = object : DiffUtil.ItemCallback<TagAdapterItem>() {
            override fun areItemsTheSame(oldItem: TagAdapterItem, newItem: TagAdapterItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: TagAdapterItem, newItem: TagAdapterItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
