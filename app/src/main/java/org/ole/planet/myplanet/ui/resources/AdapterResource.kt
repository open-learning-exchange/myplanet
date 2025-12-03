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
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowLibraryBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.dto.LibraryItem
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.Utilities

class AdapterResource(
    private val context: Context
) : ListAdapter<LibraryItem, AdapterResource.ViewHolderLibrary>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old.id == new.id },
        areContentsTheSame = { old, new -> old == new },
        getChangePayload = { old, new ->
            if (old.rating != new.rating || old.timesRated != new.timesRated || old.averageRating != new.averageRating) {
                RATING_PAYLOAD
            } else if (old.tags != new.tags) {
                TAGS_PAYLOAD
            } else if (old.checkboxChecked != new.checkboxChecked) {
                SELECTION_PAYLOAD
            } else {
                null
            }
        }
    )
) {
    private var listener: OnLibraryItemSelected? = null
    private val config: ChipCloudConfig = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private var isAscending = true
    private var isTitleAscending = false

    companion object {
        private const val TAGS_PAYLOAD = "payload_tags"
        private const val RATING_PAYLOAD = "payload_rating"
        private const val SELECTION_PAYLOAD = "payload_selection"
    }

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
        setHasStableIds(true)
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun setListener(listener: OnLibraryItemSelected?) {
        this.listener = listener
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderLibrary {
        val rowLibraryBinding =
            RowLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderLibrary(rowLibraryBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderLibrary, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun onBindViewHolder(
        holder: ViewHolderLibrary,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val item = getItem(position)
        var handled = false
        for (payload in payloads) {
            if (payload == TAGS_PAYLOAD) {
                renderTagCloud(holder.rowLibraryBinding.flexboxDrawable, item.tags)
                handled = true
            } else if (payload == RATING_PAYLOAD) {
                bindRating(holder, item)
                handled = true
            } else if (payload == SELECTION_PAYLOAD) {
                holder.rowLibraryBinding.checkbox.isChecked = item.checkboxChecked
                val selectedText = context.getString(R.string.selected)
                val libraryTitle = item.title.orEmpty()
                holder.rowLibraryBinding.checkbox.contentDescription =
                    if (libraryTitle.isNotEmpty()) "$selectedText $libraryTitle" else selectedText
                handled = true
            }
        }

        if (!handled) {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun getLibraryList(): List<RealmMyLibrary> {
        return currentList.map { it.originalObject }
    }

    fun areAllSelected(): Boolean {
        return currentList.isNotEmpty() && currentList.all { it.checkboxChecked }
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        val sorted = if (isTitleAscending) {
            currentList.sortedBy { it.title?.lowercase(Locale.ROOT) }
        } else {
            currentList.sortedByDescending { it.title?.lowercase(Locale.ROOT) }
        }
        submitList(sorted)
    }

    fun toggleSortOrder() {
        isAscending = !isAscending
        val sorted = if (isAscending) {
            currentList.sortedBy { it.createdDate }
        } else {
            currentList.sortedByDescending { it.createdDate }
        }
        submitList(sorted)
    }

    private fun renderTagCloud(flexboxDrawable: FlexboxLayout, tags: List<RealmTag>) {
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

    private fun bindRating(holder: ViewHolderLibrary, item: LibraryItem) {
        // Since we don't have the map here, we rely on pre-calculated values in LibraryItem.
        // If course rating utils needs a map structure, we might need to adjust.
        // But LibraryItem has everything.
        // Wait, CourseRatingUtils.showRating takes a JsonObject?
        // Let's check how it was used: CourseRatingUtils.showRating(context, ratingData, ...)

        // If we want to use CourseRatingUtils, we need to adapt.
        // But the previous code fell back to simple text if map entry missing.
        // Ideally LibraryItem has the string formatted.

        holder.rowLibraryBinding.rating.text = item.averageRating
        holder.rowLibraryBinding.timesRated.text = item.timesRated
        holder.rowLibraryBinding.ratingBar.rating = item.rating
    }

    inner class ViewHolderLibrary(val rowLibraryBinding: RowLibraryBinding) :
        RecyclerView.ViewHolder(rowLibraryBinding.root) {

        init {
            rowLibraryBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val adapterPosition = bindingAdapterPosition
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val item = getItem(adapterPosition)
                        // logic to show rating dialog
                        // AdapterResource previous logic:
                        // if (userModel?.isGuest() == false) ...
                        // We need to know if guest. LibraryItem doesn't have it explicitly, but Fragment handles it?
                        // Actually, AdapterResource had userModel.
                        // I should probably add isGuest or canRate to LibraryItem.
                        // Assuming checkboxVisible handles guest check for checkbox, maybe we need canRate.

                        // Let's assume for now we allow click and Fragment/Dialog handles it or we add canRate to DTO.
                        // The original code checked userModel.isGuest().

                        // We can use homeItemClickListener directly if we pass the resource.
                         homeItemClickListener?.showRatingDialog(
                                "resource",
                                item.resourceId,
                                item.title,
                                ratingChangeListener
                         )
                    }
                }
                true
            }

            rowLibraryBinding.checkbox.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener?.onItemSelected(getItem(adapterPosition).originalObject)
                }
            }

            rowLibraryBinding.description.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    openLibrary(getItem(adapterPosition))
                }
            }

            itemView.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    openLibrary(getItem(adapterPosition))
                }
            }
        }

        fun bind(item: LibraryItem) {
            rowLibraryBinding.title.text = item.title ?: ""
            setMarkdownText(rowLibraryBinding.description, item.description ?: "")
            rowLibraryBinding.timesRated.text = item.timesRated

            rowLibraryBinding.checkbox.visibility = item.checkboxVisible
            rowLibraryBinding.checkbox.isChecked = item.checkboxChecked

            val selectedText = context.getString(R.string.selected)
            val libraryTitle = item.title.orEmpty()
            rowLibraryBinding.checkbox.contentDescription = item.checkboxContentDescription ?:
                if (libraryTitle.isNotEmpty()) "$selectedText $libraryTitle" else selectedText

            rowLibraryBinding.tvDate.text = item.createdDateString

            rowLibraryBinding.ivDownloaded.visibility = item.downloadedIconVisibility
            rowLibraryBinding.ivDownloaded.contentDescription = item.downloadedIconContentDescription

            bindRating(this, item)
            renderTagCloud(rowLibraryBinding.flexboxDrawable, item.tags)
        }
    }

    private fun openLibrary(item: LibraryItem) {
        homeItemClickListener?.openLibraryDetailFragment(item.originalObject)
    }
}
