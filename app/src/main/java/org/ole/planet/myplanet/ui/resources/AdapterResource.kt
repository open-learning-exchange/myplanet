package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowLibraryBinding
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.LibraryItem
import org.ole.planet.myplanet.model.dto.TagItem
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities

class AdapterResource(
    private val context: Context,
    private var libraryList: List<LibraryItem>,
    private var ratingMap: HashMap<String?, JsonObject>,
    private val userModel: RealmUserModel?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var diffJob: Job? = null
    private val selectedItems: MutableList<LibraryItem> = ArrayList()
    private val config: ChipCloudConfig = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private var isAscending = true
    private var isTitleAscending = false
    private var onItemClickListener: ((LibraryItem) -> Unit)? = null
    private var onTagClickListener: ((TagItem) -> Unit)? = null
    private var onSelectedListChangeListener: ((List<LibraryItem>) -> Unit)? = null

    fun setOnItemClickListener(listener: (LibraryItem) -> Unit) {
        onItemClickListener = listener
    }

    fun setOnTagClickListener(listener: (TagItem) -> Unit) {
        onTagClickListener = listener
    }

    fun setOnSelectedListChangeListener(listener: (List<LibraryItem>) -> Unit) {
        onSelectedListChangeListener = listener
    }

    private data class DiffData(
        val _id: String?,
        val _rev: String?,
        val uploadDate: String?
    )

    companion object {
        private const val TAGS_PAYLOAD = "payload_tags"
        private const val RATING_PAYLOAD = "payload_rating"
        private const val SELECTION_PAYLOAD = "payload_selection"
    }

    private fun LibraryItem.toDiffData() = DiffData(
        _id = this._id,
        _rev = this._rev,
        uploadDate = this.uploadDate
    )

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun getLibraryList(): List<LibraryItem> {
        return libraryList
    }

    fun setLibraryList(libraryList: List<LibraryItem>) {
        updateList(libraryList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val rowLibraryBinding =
            RowLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderLibrary(rowLibraryBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderLibrary) {
            val library = libraryList.getOrNull(position) ?: return
            holder.bind()
            holder.rowLibraryBinding.title.text = library.title ?: ""
            setMarkdownText(holder.rowLibraryBinding.description, library.description ?: "")
            holder.rowLibraryBinding.description.setOnClickListener {
                openLibrary(library)
            }
            holder.rowLibraryBinding.timesRated.text = context.getString(R.string.num_total, library.timesRated)
            holder.rowLibraryBinding.checkbox.isChecked = selectedItems.contains(library)
            val selectedText = context.getString(R.string.selected)
            val libraryTitle = library.title.orEmpty()
            holder.rowLibraryBinding.checkbox.contentDescription =
                if (libraryTitle.isNotEmpty()) "$selectedText $libraryTitle" else selectedText
            holder.rowLibraryBinding.rating.text =
                if (TextUtils.isEmpty(library.averageRating)) {
                    "0.0"
                } else {
                    String.format(Locale.getDefault(), "%.1f", library.averageRating?.toDouble())
                }
            holder.rowLibraryBinding.tvDate.text = formatDate(library.createdDate, "MMM dd, yyyy")

            renderTagCloud(holder.rowLibraryBinding.flexboxDrawable, library.tags)

            holder.itemView.setOnClickListener {
                openLibrary(library)
            }
            if (library.resourceOffline) {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.INVISIBLE
            } else {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.VISIBLE
            }
            holder.rowLibraryBinding.ivDownloaded.contentDescription =
                if (library.resourceOffline) {
                    context.getString(R.string.view)
                } else {
                    context.getString(R.string.download)
                }
            bindRating(holder, library)

            if (userModel?.isGuest() == false) {
                holder.rowLibraryBinding.checkbox.setOnClickListener { view: View ->
                    holder.rowLibraryBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, library.title ?: "")
                    val isChecked = (view as CheckBox).isChecked
                    if (isChecked) {
                        if (!selectedItems.contains(library)) {
                            selectedItems.add(library)
                        }
                    } else {
                        selectedItems.remove(library)
                    }
                    onSelectedListChangeListener?.invoke(selectedItems)
                }
            } else {
                holder.rowLibraryBinding.checkbox.visibility = View.GONE
            }
        }
    }

    fun areAllSelected(): Boolean {
        return selectedItems.size == libraryList.size
    }

    fun selectAllItems(selectAll: Boolean) {
        if (selectAll) {
            selectedItems.clear()
            selectedItems.addAll(libraryList)
        } else {
            selectedItems.clear()
        }
        notifyItemRangeChanged(0, libraryList.size, SELECTION_PAYLOAD)
        onSelectedListChangeListener?.invoke(selectedItems)
    }

    fun areAllSelected(): Boolean {
        return selectedItems.size == libraryList.size
    }

    private fun openLibrary(library: LibraryItem) {
        onItemClickListener?.invoke(library)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (holder is ViewHolderLibrary && payloads.isNotEmpty()) {
            val library = libraryList.getOrNull(position) ?: return
            var handled = false
            if (payloads.contains(TAGS_PAYLOAD)) {
                 renderTagCloud(holder.rowLibraryBinding.flexboxDrawable, library.tags)
                 handled = true
            }
            if (payloads.contains(RATING_PAYLOAD)) {
                bindRating(holder, library)
                handled = true
            }
            if (payloads.contains(SELECTION_PAYLOAD)) {
                holder.rowLibraryBinding.checkbox.isChecked = selectedItems.contains(library)
                handled = true
            }
            if (!handled) {
                super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun renderTagCloud(flexboxDrawable: FlexboxLayout, tags: List<TagItem>) {
        flexboxDrawable.removeAllViews()
        if (tags.isEmpty()) {
            return
        }
        val chipCloud = ChipCloud(context, flexboxDrawable, config)
        tags.forEach { tag ->
            try {
                chipCloud.addChip(tag.name ?: "--")
            } catch (err: Exception) {
                chipCloud.addChip("--")
            }
        }
        chipCloud.setListener { index: Int, _: Boolean, isSelected: Boolean ->
            if (isSelected) {
                tags.getOrNull(index)?.let { selectedTag ->
                    onTagClickListener?.invoke(selectedTag)
                }
            }
        }
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        updateList(sortLibraryListByTitle())
    }

    fun toggleSortOrder() {
        isAscending = !isAscending
        updateList(sortLibraryList())
    }

    private fun sortLibraryListByTitle(): List<LibraryItem> {
        return if (isTitleAscending) {
            libraryList.sortedBy { it.title?.lowercase(Locale.ROOT) }
        } else {
            libraryList.sortedByDescending { it.title?.lowercase(Locale.ROOT) }
        }
    }

    private fun sortLibraryList(): List<LibraryItem> {
        return if (isAscending) {
            libraryList.sortedBy { it.createdDate }
        } else {
            libraryList.sortedByDescending { it.createdDate }
        }
    }

    override fun getItemCount(): Int {
        return libraryList.size
    }

    private fun updateList(newList: List<LibraryItem>) {
        diffJob?.cancel()
        val oldList = libraryList.map { it.toDiffData() }
        val newListMapped = newList.map { it.toDiffData() }

        diffJob = (context as? LifecycleOwner)?.lifecycleScope?.launch {
            val diffResult = withContext(Dispatchers.Default) {
                DiffUtils.calculateDiff(
                    oldList,
                    newListMapped,
                    areItemsTheSame = { old, new -> old._id == new._id },
                    areContentsTheSame = { old, new ->
                        old._rev == new._rev && old.uploadDate == new.uploadDate
                    }
                )
            }

            if (isActive) {
                libraryList = newList
                diffResult.dispatchUpdatesTo(this@AdapterResource)
            }
        }
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
            val index = libraryList.indexOfFirst { it.resourceId == resourceId }
            if (index != -1) {
                notifyItemChanged(index, RATING_PAYLOAD)
            }
        }
    }

    private fun bindRating(holder: ViewHolderLibrary, library: LibraryItem) {
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
                context.getString(R.string.rating_count_format, library.timesRated)
            holder.rowLibraryBinding.ratingBar.rating = averageRating
        }
    }

    internal inner class ViewHolderLibrary(val rowLibraryBinding: RowLibraryBinding) :
        RecyclerView.ViewHolder(rowLibraryBinding.root) {
            init {
                rowLibraryBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val adapterPosition = bindingAdapterPosition
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            val library = libraryList.getOrNull(adapterPosition)
                            if (userModel?.isGuest() == false) {
                                // showRatingDialog expects resourceId, title.
                                homeItemClickListener?.showRatingDialog(
                                    "resource",
                                    library?.resourceId,
                                    library?.title,
                                    ratingChangeListener
                                )
                            }
                        }
                    }
                    true
                }
            }

        fun bind() {}
    }
}
