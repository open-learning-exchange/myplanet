package org.ole.planet.myplanet.ui.resources

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.CheckBox
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import kotlin.collections.HashMap
import kotlin.collections.List
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowAdapterNavigationChildBinding
import org.ole.planet.myplanet.databinding.RowAdapterNavigationParentBinding
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utilities.DiffUtils
import androidx.recyclerview.widget.RecyclerView

sealed class TagListItem {
    data class Parent(val tagParent: TagParent) : TagListItem()
    data class Child(val tagChild: TagChild) : TagListItem()
}

data class TagParent(
    val tag: RealmTag,
    var isExpanded: Boolean = false
)

data class TagChild(
    val tag: RealmTag
)

class TagExpandableAdapter(
    private var tagList: List<RealmTag>,
    private val childMap: HashMap<String, List<RealmTag>>,
    private val selectedItemsList: ArrayList<RealmTag>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_PARENT = 0
        private const val VIEW_TYPE_CHILD = 1
    }

    private var clickListener: OnClickTagItem? = null
    private var isSelectMultiple = false
    private var displayList = mutableListOf<TagListItem>()

    init {
        displayList = buildDisplayList().toMutableList()
    }

    private fun buildDisplayList(): List<TagListItem> {
        val list = mutableListOf<TagListItem>()
        tagList.forEach { parentTag ->
            val parent = TagParent(tag = parentTag)
            list.add(TagListItem.Parent(parent))
            if (parent.isExpanded) {
                childMap[parentTag.id]?.forEach { childTag ->
                    list.add(TagListItem.Child(TagChild(tag = childTag)))
                }
            }
        }
        return list
    }

    fun setSelectMultiple(selectMultiple: Boolean) {
        isSelectMultiple = selectMultiple
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is TagListItem.Parent -> VIEW_TYPE_PARENT
            is TagListItem.Child -> VIEW_TYPE_CHILD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PARENT -> {
                val binding = RowAdapterNavigationParentBinding.inflate(inflater, parent, false)
                ParentViewHolder(binding)
            }
            else -> {
                val binding = RowAdapterNavigationChildBinding.inflate(inflater, parent, false)
                ChildViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ParentViewHolder -> {
                val parent = (displayList[position] as TagListItem.Parent).tagParent
                holder.bind(parent)
            }
            is ChildViewHolder -> {
                val child = (displayList[position] as TagListItem.Child).tagChild
                holder.bind(child)
            }
        }
    }

    override fun getItemCount(): Int = displayList.size

    inner class ParentViewHolder(private val binding: RowAdapterNavigationParentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(parent: TagParent) {
            binding.tvDrawerTitle1.text = parent.tag.name
            createCheckbox(binding.root, parent.tag)
            binding.tvDrawerTitle.text = parent.tag.name

            if (!childMap.containsKey(parent.tag.id)) {
                binding.tvDrawerTitle1.visibility = View.VISIBLE
                binding.tvDrawerTitle.visibility = View.GONE
                binding.ivIndicators.visibility = View.GONE
                binding.tvDrawerTitle1.setOnClickListener { clickListener?.onTagClicked(parent.tag) }
            } else {
                binding.tvDrawerTitle.visibility = View.VISIBLE
                binding.tvDrawerTitle1.setOnClickListener(null)
                binding.tvDrawerTitle1.visibility = View.GONE
                binding.ivIndicators.visibility = View.VISIBLE
                setExpandedIcon(parent.isExpanded, binding.ivIndicators)
                binding.tvDrawerTitle.setOnClickListener {
                    parent.isExpanded = !parent.isExpanded
                    val newList = buildDisplayList()
                    updateList(newList)
                }
            }
        }
    }

    inner class ChildViewHolder(private val binding: RowAdapterNavigationChildBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(child: TagChild) {
            createCheckbox(binding.root, child.tag)
            binding.tvDrawerTitle.text = child.tag.name
            binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.multi_select_grey))
            binding.tvDrawerTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.daynight_textColor))
            binding.tvDrawerTitle.setOnClickListener { clickListener?.onTagClicked(child.tag) }
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

    fun setClickListener(clickListener: OnClickTagItem) {
        this.clickListener = clickListener
    }

    fun setTagList(filteredList: List<RealmTag>) {
        this.tagList = filteredList
        val newList = buildDisplayList()
        updateList(newList)
    }

    private fun updateList(newList: List<TagListItem>) {
        val diffResult = DiffUtils.calculateDiff(displayList, newList,
            areItemsTheSame = { old, new ->
                when {
                    old is TagListItem.Parent && new is TagListItem.Parent -> old.tagParent.tag.id == new.tagParent.tag.id
                    old is TagListItem.Child && new is TagListItem.Child -> old.tagChild.tag.id == new.tagChild.tag.id
                    else -> false
                }
            },
            areContentsTheSame = { old, new ->
                when {
                    old is TagListItem.Parent && new is TagListItem.Parent -> old.tagParent == new.tagParent
                    old is TagListItem.Child && new is TagListItem.Child -> old.tagChild == new.tagChild
                    else -> false
                }
            }
        )
        displayList = newList.toMutableList()
        diffResult.dispatchUpdatesTo(this)
    }

    interface OnClickTagItem {
        fun onTagClicked(tag: RealmTag)
        fun onCheckboxTagSelected(tags: RealmTag)
    }
}
