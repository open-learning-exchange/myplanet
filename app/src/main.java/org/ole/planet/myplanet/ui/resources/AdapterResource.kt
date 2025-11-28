package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.text.SpannableString
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowLibraryBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterResource(
    private val context: Context,
    private var ratingMap: HashMap<String?, JsonObject>,
    private var tagSpannables: Map<String, SpannableString>,
    private val userModel: RealmUserModel?
) : ListAdapter<RealmMyLibrary, AdapterResource.ViewHolderLibrary>(DIFF_CALLBACK) {
    private val selectedItems: MutableList<RealmMyLibrary> = ArrayList()
    private var listener: OnLibraryItemSelected? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private var isAscending = true
    private var isTitleAscending = false

    companion object {
        private const val RATING_PAYLOAD = "payload_rating"
        private const val SELECTION_PAYLOAD = "payload_selection"

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RealmMyLibrary>() {
            override fun areItemsTheSame(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Boolean {
                return oldItem.title == newItem.title &&
                        oldItem.description == newItem.description &&
                        oldItem.createdDate == newItem.createdDate &&
                        oldItem.averageRating == newItem.averageRating &&
                        oldItem.timesRated == newItem.timesRated
            }

            override fun getChangePayload(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Any? {
                val ratingChanged = oldItem.averageRating != newItem.averageRating || oldItem.timesRated != newItem.timesRated
                val otherContentChanged = oldItem.title != newItem.title ||
                        oldItem.description != newItem.description ||
                        oldItem.createdDate != newItem.createdDate

                if (ratingChanged && !otherContentChanged) {
                    return RATING_PAYLOAD
                }
                return null
            }
        }
    }

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun setListener(listener: OnLibraryItemSelected?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderLibrary {
        val rowLibraryBinding =
            RowLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderLibrary(rowLibraryBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderLibrary, position: Int) {
        val library = getItem(position)
        holder.bind(library)
    }

    override fun onBindViewHolder(holder: ViewHolderLibrary, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val library = getItem(position)
            payloads.forEach { payload ->
                when (payload) {
                    RATING_PAYLOAD -> bindRating(holder, library)
                    SELECTION_PAYLOAD -> holder.rowLibraryBinding.checkbox.isChecked = selectedItems.contains(library)
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun areAllSelected(): Boolean {
        return selectedItems.size == currentList.size
    }

    fun selectAllItems(selectAll: Boolean) {
        if (selectAll) {
            selectedItems.clear()
            selectedItems.addAll(currentList)
        } else {
            selectedItems.clear()
        }
        notifyItemRangeChanged(0, currentList.size, SELECTION_PAYLOAD)
        listener?.onSelectedListChange(selectedItems)
    }

    private fun openLibrary(library: RealmMyLibrary?) {
        homeItemClickListener?.openLibraryDetailFragment(library)
    }

    fun setRatingMap(newRatingMap: HashMap<String?, JsonObject>) {
        val updatedResourceIds = mutableSetOf<String?>()

        newRatingMap.forEach { (resourceId, newRating) ->
            if (ratingMap[resourceId] != newRating) {
                updatedResourceIds.add(resourceId)
            }
        }

        ratingMap.keys.filterNot { newRatingMap.containsKey(it) }.forEach { removedKey ->
            updatedResourceIds.add(removedKey)
        }

        ratingMap.clear()
        ratingMap.putAll(newRatingMap)

        updatedResourceIds.forEach { resourceId ->
            if (resourceId.isNullOrEmpty()) {
                return@forEach
            }
            val index = currentList.indexOfFirst { it.resourceId == resourceId }
            if (index != -1) {
                notifyItemChanged(index, RATING_PAYLOAD)
            }
        }
    }

    private fun bindRating(holder: ViewHolderLibrary, library: RealmMyLibrary) {
        if (ratingMap.containsKey(library.resourceId)) {
            val ratingData = ratingMap[library.resourceId]
            CourseRatingUtils.showRating(
                context,
                ratingData,
                holder.rowLibraryBinding.rating,
                holder.rowLibraryBinding.timesRated,
                holder.rowLibraryBinding.ratingBar
            )
        } else {
            val averageRating = library.averageRating?.toFloatOrNull() ?: 0f
            holder.rowLibraryBinding.rating.text = String.format(Locale.getDefault(), "%.2f", averageRating)
            holder.rowLibraryBinding.timesRated.text =
                context.getString(R.string.rating_count_format, library.timesRated ?: 0)
            holder.rowLibraryBinding.ratingBar.rating = averageRating
        }
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        val sortedList = if (isTitleAscending) {
            currentList.sortedBy { it.title?.lowercase(Locale.ROOT) }
        } else {
            currentList.sortedByDescending { it.title?.lowercase(Locale.ROOT) }
        }
        submitList(sortedList)
    }

    fun toggleSortOrder() {
        isAscending = !isAscending
        val sortedList = if (isAscending) {
            currentList.sortedBy { it.createdDate }
        } else {
            currentList.sortedByDescending { it.createdDate }
        }
        submitList(sortedList)
    }

    fun updateTagSpannables(newTagSpannables: Map<String, SpannableString>) {
        tagSpannables = newTagSpannables
        notifyDataSetChanged()
    }

    internal inner class ViewHolderLibrary(val rowLibraryBinding: RowLibraryBinding) :
        RecyclerView.ViewHolder(rowLibraryBinding.root) {
        init {
            rowLibraryBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val adapterPosition = bindingAdapterPosition
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val library = getItem(adapterPosition)
                        if (userModel?.isGuest() == false) {
                            homeItemClickListener?.showRatingDialog(
                                "resource",
                                library.resourceId,
                                library.title,
                                ratingChangeListener
                            )
                        }
                    }
                }
                true
            }
        }

        fun bind(library: RealmMyLibrary) {
            rowLibraryBinding.title.text = library.title ?: ""
            setMarkdownText(rowLibraryBinding.description, library.description ?: "")
            rowLibraryBinding.description.setOnClickListener { openLibrary(library) }
            rowLibraryBinding.timesRated.text = context.getString(R.string.num_total, library.timesRated)
            rowLibraryBinding.checkbox.isChecked = selectedItems.contains(library)
            val selectedText = context.getString(R.string.selected)
            val libraryTitle = library.title.orEmpty()
            rowLibraryBinding.checkbox.contentDescription =
                if (libraryTitle.isNotEmpty()) "$selectedText $libraryTitle" else selectedText
            rowLibraryBinding.rating.text =
                if (TextUtils.isEmpty(library.averageRating)) "0.0"
                else String.format(Locale.getDefault(), "%.1f", library.averageRating?.toDouble())

            rowLibraryBinding.tvDate.text = library.createdDate?.let { formatDate(it, "MMM dd, yyyy") }
            rowLibraryBinding.tvTags.text = tagSpannables[library.id]

            itemView.setOnClickListener { openLibrary(library) }

            rowLibraryBinding.ivDownloaded.visibility =
                if (library.isResourceOffline() == true) View.INVISIBLE else View.VISIBLE
            rowLibraryBinding.ivDownloaded.contentDescription =
                if (library.isResourceOffline() == true) context.getString(R.string.view)
                else context.getString(R.string.download)

            bindRating(this, library)

            if (userModel?.isGuest() == false) {
                rowLibraryBinding.checkbox.setOnClickListener { view: View ->
                    rowLibraryBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, library.title ?: "")
                    val isChecked = (view as CheckBox).isChecked
                    if (isChecked) {
                        if (!selectedItems.contains(library)) {
                            selectedItems.add(library)
                        }
                    } else {
                        selectedItems.remove(library)
                    }
                    listener?.onSelectedListChange(selectedItems)
                }
            } else {
                rowLibraryBinding.checkbox.visibility = View.GONE
            }
        }
    }
}
