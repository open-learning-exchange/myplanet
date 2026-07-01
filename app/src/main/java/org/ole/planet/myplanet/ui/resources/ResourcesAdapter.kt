package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelectedListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowLibraryBinding
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagItem
import org.ole.planet.myplanet.utils.CourseRatingUtils
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.Utilities.getCloudConfig

class ResourcesAdapter(
    private val context: Context,
    private val isGuest: Boolean,
    private var openedResourceIds: Set<String>
) : ListAdapter<ResourceListModel, RecyclerView.ViewHolder>(ITEM_CALLBACK) {

    private val selectedItemIds = mutableSetOf<String>()
    private val selectedItemsMap = LinkedHashMap<String, org.ole.planet.myplanet.model.ResourceItem>()
    private var listener: OnLibraryItemSelectedListener? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private val config: ChipCloudConfig = getCloudConfig().selectMode(ChipCloud.SelectMode.single)

    private var isAscending = true
    private var isTitleAscending = true

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
    }

    companion object {
        private const val SELECTION_PAYLOAD = "SELECTION_PAYLOAD"
        private const val RATING_PAYLOAD = "RATING_PAYLOAD"
        private const val OPENED_RESOURCE_PAYLOAD = "OPENED_RESOURCE_PAYLOAD"
        private const val TAGS_PAYLOAD = "TAGS_PAYLOAD"
        private const val OFFLINE_STATUS_PAYLOAD = "OFFLINE_STATUS_PAYLOAD"

        private val ITEM_CALLBACK = DiffUtils.itemCallback<ResourceListModel>(
            areItemsTheSame = { oldItem, newItem ->
                oldItem.item.id == newItem.item.id
            },
            areContentsTheSame = { oldItem, newItem ->
                oldItem == newItem
            },
            getChangePayload = { oldItem, newItem ->
                val payloads = mutableListOf<String>()
                if (oldItem.isOpened != newItem.isOpened) {
                    payloads.add(OPENED_RESOURCE_PAYLOAD)
                }
                if (oldItem.item.isOffline != newItem.item.isOffline || oldItem.isLocallyOffline != newItem.isLocallyOffline) {
                    payloads.add(OFFLINE_STATUS_PAYLOAD)
                }
                payloads.ifEmpty { null }
            }
        )
    }

    private val locallyOfflineIds = mutableSetOf<String>()

    fun markItemAsOffline(id: String) {
        if (locallyOfflineIds.add(id)) {
            val newList = currentList.map {
                if (it.item.id == id) it.copy(isLocallyOffline = true) else it
            }
            submitList(newList)
        }
    }

    fun setListener(listener: OnLibraryItemSelectedListener?) {
        this.listener = listener
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun getLibraryList(): List<ResourceListModel> {
        return currentList
    }

    fun setLibraryList(libraryList: List<ResourceListModel?>, onComplete: (() -> Unit)? = null) {
        val updatedList = libraryList.filterNotNull().map {
            it.copy(
                isOpened = openedResourceIds.contains(it.item.id),
                isLocallyOffline = locallyOfflineIds.contains(it.item.id)
            )
        }
        submitList(updatedList, onComplete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val rowLibraryBinding = RowLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ResourcesViewHolder(rowLibraryBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ResourcesViewHolder) {
            val model = getItem(position) ?: return
            val library = model.item
            holder.rowLibraryBinding.title.text = library.title
            holder.rowLibraryBinding.description.text = library.description
            holder.rowLibraryBinding.timesRated.text = context.getString(R.string.rating_count_format, library.timesRated)
            holder.rowLibraryBinding.checkbox.isChecked = selectedItemIds.contains(model.item.id)
            holder.rowLibraryBinding.rating.text = if (TextUtils.isEmpty(library.averageRating)) "0.0" else String.format(Locale.getDefault(), "%.1f", library.averageRating?.toDoubleOrNull() ?: 0.0)
            holder.rowLibraryBinding.tvDate.text = org.ole.planet.myplanet.utils.TimeUtils.formatDate(library.createdDate)

            displayTagCloud(holder, position)
            holder.itemView.setOnClickListener {
                openLibrary(model)
            }
            val isResourceOpened = model.isOpened
            val isOffline = library.isOffline || model.isLocallyOffline
            holder.rowLibraryBinding.ivDownloaded.visibility =
                if (isOffline || isResourceOpened) View.INVISIBLE else View.VISIBLE
            holder.rowLibraryBinding.ivDownloaded.contentDescription =
                if (isOffline) {
                    context.getString(R.string.view)
                } else {
                    context.getString(R.string.download)
                }
            bindRating(holder, model)

            if (!isGuest) {
                holder.rowLibraryBinding.checkbox.setOnClickListener { view: View ->
                    holder.rowLibraryBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, library.title ?: "")
                    val isChecked = (view as CheckBox).isChecked
                    model.item.id?.let { itemId ->
                        if (isChecked) {
                            selectedItemIds.add(itemId)
                            selectedItemsMap[itemId] = model.item
                        } else {
                            selectedItemIds.remove(itemId)
                            selectedItemsMap.remove(itemId)
                        }
                    }
                    listener?.onSelectedListChange(selectedItemsMap.values.toList())
                }
            } else {
                holder.rowLibraryBinding.checkbox.visibility = View.GONE
            }
        }
    }

    fun areAllSelected(): Boolean {
        return currentList.isNotEmpty() && selectedItemIds.size == currentList.size
    }

    fun selectAllItems(selectAll: Boolean) {
        if (selectAll) {
            currentList.forEachIndexed { index, model ->
                model.item.id?.let { itemId ->
                    if (selectedItemIds.add(itemId)) {
                        selectedItemsMap[itemId] = model.item
                        notifyItemChanged(index, SELECTION_PAYLOAD)
                    }
                }
            }
        } else {
            currentList.forEachIndexed { index, model ->
                model.item.id?.let { itemId ->
                    if (selectedItemIds.remove(itemId)) {
                        selectedItemsMap.remove(itemId)
                        notifyItemChanged(index, SELECTION_PAYLOAD)
                    }
                }
            }
        }

        listener?.onSelectedListChange(selectedItemsMap.values.toList())
    }

    private fun openLibrary(model: ResourceListModel) {
        listener?.onResourceClicked(model.item)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (holder is ResourcesViewHolder && payloads.isNotEmpty()) {
            val model = getItem(position) ?: return
            val library = model.item
            var handled = false

            val flatPayloads = payloads.flatMap { if (it is List<*>) it else listOf(it) }

            if (flatPayloads.contains(RATING_PAYLOAD)) {
                bindRating(holder, model)
                handled = true
            }
            if (flatPayloads.contains(SELECTION_PAYLOAD)) {
                holder.rowLibraryBinding.checkbox.isChecked = selectedItemIds.contains(model.item.id)
                handled = true
            }
            if (flatPayloads.contains(OPENED_RESOURCE_PAYLOAD) || flatPayloads.contains(OFFLINE_STATUS_PAYLOAD)) {
                val isResourceOpened = model.isOpened
                val isOffline = library.isOffline || model.isLocallyOffline
                holder.rowLibraryBinding.ivDownloaded.visibility =
                    if (isOffline || isResourceOpened) View.INVISIBLE else View.VISIBLE
                handled = true
            }
            if (flatPayloads.contains(TAGS_PAYLOAD)) {
                displayTagCloud(holder, position)
                handled = true
            }
            if (!handled) {
                super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun setOpenedResourceIds(openedResourceIds: Set<String>) {
        this.openedResourceIds = openedResourceIds
        val newList = currentList.map { it.copy(isOpened = openedResourceIds.contains(it.item.id)) }
        submitList(newList)
    }

    private fun displayTagCloud(holder: ResourcesViewHolder, position: Int) {
        val flexboxDrawable = holder.rowLibraryBinding.flexboxDrawable
        val model = getItem(position)
        if (model == null) {
            holder.cachedTags = emptyList()
            flexboxDrawable.removeAllViews()
            return
        }
        val tags = model.tags
        if (tags == holder.cachedTags) return
        holder.cachedTags = tags
        renderTagCloud(flexboxDrawable, tags)
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
                    listener?.onTagClicked(selectedTag)
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ResourcesViewHolder) {
            holder.cachedTags = emptyList()
            holder.rowLibraryBinding.flexboxDrawable.removeAllViews()
        }
    }

    fun toggleTitleSortOrder(onComplete: (() -> Unit)? = null) {
        isTitleAscending = !isTitleAscending
        setLibraryList(sortLibraryListByTitle(), onComplete)
    }

    fun toggleSortOrder(onComplete: (() -> Unit)? = null) {
        isAscending = !isAscending
        setLibraryList(sortLibraryList(), onComplete)
    }

    private fun sortLibraryListByTitle(): List<ResourceListModel> {
        return if (isTitleAscending) {
            currentList.sortedBy { it.item.title?.lowercase(Locale.ROOT) }
        } else {
            currentList.sortedByDescending { it.item.title?.lowercase(Locale.ROOT) }
        }
    }

    private fun sortLibraryList(): List<ResourceListModel> {
        return if (isAscending) {
            currentList.sortedBy { it.item.createdDate }
        } else {
            currentList.sortedByDescending { it.item.createdDate }
        }
    }

    private fun bindRating(holder: ResourcesViewHolder, model: ResourceListModel) {
        if (model.rating != null) {
            CourseRatingUtils.showRating(
                context,
                model.rating,
                holder.rowLibraryBinding.rating,
                holder.rowLibraryBinding.timesRated,
                holder.rowLibraryBinding.ratingBar
            )
        } else {
            val averageRating = model.item.averageRating?.toFloatOrNull() ?: 0f
            holder.rowLibraryBinding.rating.text = String.format(Locale.getDefault(), "%.2f", averageRating)
            holder.rowLibraryBinding.timesRated.text =
                context.getString(R.string.rating_count_format, model.item.timesRated)
            holder.rowLibraryBinding.ratingBar.rating = averageRating
        }
    }

    internal inner class ResourcesViewHolder(val rowLibraryBinding: RowLibraryBinding) :
        RecyclerView.ViewHolder(rowLibraryBinding.root) {
        var cachedTags: List<TagItem> = emptyList()
        init {
                rowLibraryBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val adapterPosition = bindingAdapterPosition
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            if (adapterPosition < currentList.size) {
                                val model = getItem(adapterPosition)
                                if (!isGuest) {
                                    homeItemClickListener?.showRatingDialog(
                                        "resource",
                                        model?.item?.resourceId,
                                        model?.item?.title,
                                        ratingChangeListener
                                    )
                                }
                            }
                        }
                    }
                    true
                }
            }

        fun bind() {}
    }
}
