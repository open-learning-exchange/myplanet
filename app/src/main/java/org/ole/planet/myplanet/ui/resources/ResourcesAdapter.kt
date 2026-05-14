package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
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

class ResourcesAdapter(
    private val context: Context,
    private val isGuest: Boolean,
    private var openedResourceIds: Set<String>
) : ListAdapter<ResourceListModel, RecyclerView.ViewHolder>(
    org.ole.planet.myplanet.utils.DiffUtils.itemCallback<ResourceListModel>(
        { oldItem, newItem -> oldItem.item.id == newItem.item.id },
        { oldItem, newItem -> oldItem == newItem }
    )
) {

    private val selectedItems: MutableList<ResourceListModel> = ArrayList()
    private var listener: OnLibraryItemSelectedListener? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private val config: ChipCloudConfig = org.ole.planet.myplanet.utils.Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)

    private var isAscending = true
    private var isTitleAscending = true

    companion object {
        private const val SELECTION_PAYLOAD = "SELECTION_PAYLOAD"
        private const val RATING_PAYLOAD = "RATING_PAYLOAD"
        private const val OPENED_RESOURCE_PAYLOAD = "OPENED_RESOURCE_PAYLOAD"
        private const val TAGS_PAYLOAD = "TAGS_PAYLOAD"
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
        submitList(libraryList.filterNotNull(), onComplete)
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
            holder.rowLibraryBinding.checkbox.isChecked = selectedItems.contains(model)
            holder.rowLibraryBinding.rating.text = if (TextUtils.isEmpty(library.averageRating)) "0.0" else String.format(Locale.getDefault(), "%.1f", library.averageRating?.toDoubleOrNull() ?: 0.0)
            holder.rowLibraryBinding.tvDate.text = org.ole.planet.myplanet.utils.TimeUtils.formatDate(library.createdDate)

            displayTagCloud(holder, position)
            holder.itemView.setOnClickListener {
                openLibrary(model)
            }
            val isResourceOpened = openedResourceIds.contains(library.id)
            if (library.isOffline || isResourceOpened) {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.INVISIBLE
            } else {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.VISIBLE
            }
            holder.rowLibraryBinding.ivDownloaded.contentDescription =
                if (library.isOffline) {
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
                    if (isChecked) {
                        if (!selectedItems.contains(model)) {
                            selectedItems.add(model)
                        }
                    } else {
                        selectedItems.remove(model)
                    }
                    if (listener != null) listener?.onSelectedListChange(selectedItems.map { it.item })
                }
            } else {
                holder.rowLibraryBinding.checkbox.visibility = View.GONE
            }
        }
    }

    fun areAllSelected(): Boolean {
        return currentList.isNotEmpty() && selectedItems.size == currentList.size
    }

    fun selectAllItems(selectAll: Boolean) {
        if (selectAll) {
            selectedItems.clear()
            selectedItems.addAll(currentList)
        } else {
            selectedItems.clear()
        }

        notifyItemRangeChanged(0, currentList.size, SELECTION_PAYLOAD)

        if (listener != null) {
            listener?.onSelectedListChange(selectedItems.map { it.item })
        }
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
            if (payloads.contains(RATING_PAYLOAD)) {
                bindRating(holder, model)
                handled = true
            }
            if (payloads.contains(SELECTION_PAYLOAD)) {
                holder.rowLibraryBinding.checkbox.isChecked = selectedItems.contains(model)
                handled = true
            }
            if (payloads.contains(OPENED_RESOURCE_PAYLOAD)) {
                val isResourceOpened = openedResourceIds.contains(library.id)
                if (library.isOffline || isResourceOpened) {
                    holder.rowLibraryBinding.ivDownloaded.visibility = View.INVISIBLE
                } else {
                    holder.rowLibraryBinding.ivDownloaded.visibility = View.VISIBLE
                }
                handled = true
            }
            if (payloads.contains(TAGS_PAYLOAD)) {
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
        val oldOpenedResourceIds = this.openedResourceIds
        this.openedResourceIds = openedResourceIds
        currentList.forEachIndexed { index, model ->
            val wasOpened = oldOpenedResourceIds.contains(model.item.id)
            val isOpened = openedResourceIds.contains(model.item.id)
            if (wasOpened != isOpened) {
                notifyItemChanged(index, OPENED_RESOURCE_PAYLOAD)
            }
        }
    }

    private fun displayTagCloud(holder: ResourcesViewHolder, position: Int) {
        val flexboxDrawable = holder.rowLibraryBinding.flexboxDrawable
        val model = getItem(position)
        if (model == null) {
            flexboxDrawable.removeAllViews()
            return
        }
        val tags = model.tags
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
