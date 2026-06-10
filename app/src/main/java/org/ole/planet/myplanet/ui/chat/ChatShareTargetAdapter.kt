package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.ListUpdateCallback
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.DiffUtils

data class ShareTargetItem(
    val title: String,
    val details: List<String>
)

class ChatShareTargetAdapter(
    private val context: Context,
    expandableTitleList: List<String>,
    expandableDetailList: HashMap<String, List<String>>,
    private var sharedChildren: Set<String> = emptySet()
) : BaseExpandableListAdapter() {

    private val diffCallback = DiffUtils.itemCallback<ShareTargetItem>(
        { oldItem, newItem -> oldItem.title == newItem.title },
        { oldItem, newItem -> oldItem == newItem }
    )

    private val differ = AsyncListDiffer(
        object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) { notifyDataSetChanged() }
            override fun onRemoved(position: Int, count: Int) { notifyDataSetChanged() }
            override fun onMoved(fromPosition: Int, toPosition: Int) { notifyDataSetChanged() }
            override fun onChanged(position: Int, count: Int, payload: Any?) { notifyDataSetChanged() }
        },
        AsyncDifferConfig.Builder(diffCallback).build()
    )

    init {
        updateData(expandableTitleList, expandableDetailList, sharedChildren)
    }

    fun updateData(newTitleList: List<String>, newDetailList: HashMap<String, List<String>>, newSharedChildren: Set<String> = sharedChildren) {
        sharedChildren = newSharedChildren
        val newItems = newTitleList.map { title ->
            ShareTargetItem(title, newDetailList[title] ?: emptyList())
        }
        differ.submitList(newItems)
    }

    override fun getChild(lstPosn: Int, expandedListPosition: Int): Any {
        return differ.currentList[lstPosn].details[expandedListPosition]
    }

    override fun getChildId(listPosition: Int, expandedListPosition: Int): Long {
        return expandedListPosition.toLong()
    }

    override fun getChildView(lstPosn: Int, expandedListPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup): View {
        var reusedView = convertView
        val expandedListText = getChild(lstPosn, expandedListPosition) as String
        if (reusedView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            reusedView = layoutInflater.inflate(R.layout.expandable_list_item, parent, false)
        }
        val expandedListTextView = reusedView.findViewById<View>(R.id.expandedListItem) as TextView
        val sharedIcon = reusedView.findViewById<ImageView>(R.id.sharedIcon)
        expandedListTextView.text = expandedListText
        reusedView.setBackgroundColor(ContextCompat.getColor(parent.context, R.color.multi_select_grey))
        expandedListTextView.setTextColor(ContextCompat.getColor(parent.context, R.color.daynight_textColor))
        sharedIcon?.visibility = if (expandedListText in sharedChildren) View.VISIBLE else View.GONE
        return reusedView
    }

    override fun getChildrenCount(listPosition: Int): Int {
        return differ.currentList[listPosition].details.size
    }

    override fun getGroup(listPosition: Int): Any {
        return differ.currentList[listPosition].title
    }

    override fun getGroupCount(): Int {
        return differ.currentList.size
    }

    override fun getGroupId(listPosition: Int): Long {
        return listPosition.toLong()
    }

    override fun getGroupView(listPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup): View {
        var reusedView = convertView
        val listTitle = getGroup(listPosition) as String
        if (reusedView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            reusedView = layoutInflater.inflate(R.layout.expandable_list_group, parent, false)
        }
        val listTitleTextView = reusedView.findViewById<View>(R.id.listTitle) as TextView
        val arrowIcon = reusedView.findViewById<ImageView>(R.id.arrowIcon)
        listTitleTextView.setTypeface(null, Typeface.BOLD)
        listTitleTextView.text = listTitle
        listTitleTextView.setTextColor(ContextCompat.getColor(parent.context, R.color.daynight_textColor))
        arrowIcon?.rotation = if (isExpanded) 180f else 0f
        return reusedView
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun isChildSelectable(listPosition: Int, expandedListPosition: Int): Boolean {
        return true
    }
}
