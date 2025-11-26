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

data class TagDisplayItem(
    val tag: RealmTag,
    val isParent: Boolean,
    var isExpanded: Boolean = false
)

class TagAdapter(
    private val childMap: HashMap<String, List<RealmTag>>,
    private val selectedItemsList: ArrayList<RealmTag>
) : ListAdapter<TagDisplayItem, RecyclerView.ViewHolder>(TAG_DISPLAY_ITEM_COMPARATOR) {

    private var clickListener: OnClickTagItem? = null
    private var isSelectMultiple = false

    fun setSelectMultiple(selectMultiple: Boolean) {
        isSelectMultiple = selectMultiple
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item.isParent) VIEW_TYPE_PARENT else VIEW_TYPE_CHILD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_PARENT) {
            val binding = RowAdapterNavigationParentBinding.inflate(inflater, parent, false)
            ParentViewHolder(binding)
        } else {
            val binding = RowAdapterNavigationChildBinding.inflate(inflater, parent, false)
            ChildViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is ParentViewHolder -> holder.bind(item)
            is ChildViewHolder -> holder.bind(item.tag)
        }
    }

    fun setClickListener(clickListener: OnClickTagItem) {
        this.clickListener = clickListener
    }

    inner class ParentViewHolder(private val binding: RowAdapterNavigationParentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TagDisplayItem) {
            val tag = item.tag
            binding.tvDrawerTitle.text = tag.name
            binding.tvDrawerTitle1.text = tag.name
            setExpandedIcon(item.isExpanded, binding.ivIndicators)

            if (!childMap.containsKey(tag.id)) {
                binding.tvDrawerTitle1.visibility = View.VISIBLE
                binding.tvDrawerTitle.visibility = View.GONE
                binding.ivIndicators.visibility = View.GONE
                binding.tvDrawerTitle1.setOnClickListener { clickListener?.onTagClicked(tag) }
            } else {
                binding.tvDrawerTitle.visibility = View.VISIBLE
                binding.tvDrawerTitle1.visibility = View.GONE
                binding.ivIndicators.visibility = View.VISIBLE
                binding.tvDrawerTitle.setOnClickListener { clickListener?.onTagClicked(tag) }
            }

            itemView.setOnClickListener {
                item.isExpanded = !item.isExpanded
                updateList()
            }
            createCheckbox(itemView, tag)
        }
    }

    inner class ChildViewHolder(private val binding: RowAdapterNavigationChildBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(tag: RealmTag) {
            binding.tvDrawerTitle.text = tag.name
            binding.root.setBackgroundColor(
                ContextCompat.getColor(
                    itemView.context,
                    R.color.multi_select_grey
                )
            )
            binding.tvDrawerTitle.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    R.color.daynight_textColor
                )
            )
            binding.tvDrawerTitle.setOnClickListener { clickListener?.onTagClicked(tag) }
            createCheckbox(itemView, tag)
        }
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

    private var sourceList: List<TagDisplayItem> = emptyList()
    fun setData(tags: List<RealmTag>) {
        sourceList = tags.map { TagDisplayItem(it, childMap.containsKey(it.id)) }
        updateList()
    }

    fun updateList() {
        val displayList = mutableListOf<TagDisplayItem>()
        sourceList.forEach { item ->
            displayList.add(item)
            if (item.isExpanded) {
                childMap[item.tag.id]?.let { children ->
                    displayList.addAll(children.map { TagDisplayItem(it, false) })
                }
            }
        }
        submitList(displayList)
    }


    interface OnClickTagItem {
        fun onTagClicked(tag: RealmTag)
        fun onCheckboxTagSelected(tags: RealmTag)
    }

    companion object {
        private const val VIEW_TYPE_PARENT = 0
        private const val VIEW_TYPE_CHILD = 1

        private val TAG_DISPLAY_ITEM_COMPARATOR = object : DiffUtil.ItemCallback<TagDisplayItem>() {
            override fun areItemsTheSame(oldItem: TagDisplayItem, newItem: TagDisplayItem): Boolean {
                return oldItem.tag.id == newItem.tag.id
            }

            override fun areContentsTheSame(oldItem: TagDisplayItem, newItem: TagDisplayItem): Boolean {
                return oldItem.tag.name == newItem.tag.name && oldItem.isExpanded == newItem.isExpanded
            }
        }
    }
}
