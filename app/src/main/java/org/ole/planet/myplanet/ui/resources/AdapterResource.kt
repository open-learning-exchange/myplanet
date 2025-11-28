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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowLibraryBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import kotlinx.coroutines.Job
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities

class AdapterResource(
    private val context: Context,
    private var libraryList: List<RealmMyLibrary?>,
    private var ratingMap: HashMap<String?, JsonObject>,
    private val tagRepository: TagRepository,
    private val userModel: RealmUserModel?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var diffJob: Job? = null
    private val selectedItems: MutableList<RealmMyLibrary?> = ArrayList()
    private var listener: OnLibraryItemSelected? = null
    private val config: ChipCloudConfig = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private var isAscending = true
    private var isTitleAscending = false
    private val tagCache: MutableMap<String, List<RealmTag>> = mutableMapOf()
    private val tagRequestsInProgress: MutableSet<String> = mutableSetOf()

    companion object {
        private const val TAGS_PAYLOAD = "payload_tags"
        private const val RATING_PAYLOAD = "payload_rating"
        private const val SELECTION_PAYLOAD = "payload_selection"
    }

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun getLibraryList(): List<RealmMyLibrary?> {
        return libraryList
    }

    fun setLibraryList(libraryList: List<RealmMyLibrary?>) {
        if (this.libraryList === libraryList) return
        updateList(libraryList)
    }

    fun setListener(listener: OnLibraryItemSelected?) {
        this.listener = listener
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
            holder.rowLibraryBinding.tvDate.text = library.createdDate?.let { formatDate(it, "MMM dd, yyyy") }
            displayTagCloud(holder, position)
            holder.itemView.setOnClickListener {
                openLibrary(library)
            }
            if (library.isResourceOffline() == true) {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.INVISIBLE
            } else {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.VISIBLE
            }
            holder.rowLibraryBinding.ivDownloaded.contentDescription =
                if (library.isResourceOffline() == true) {
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
                    if (listener != null) listener?.onSelectedListChange(selectedItems)
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
        if (listener != null) {
            listener?.onSelectedListChange(selectedItems)
        }
    }

    private fun openLibrary(library: RealmMyLibrary?) {
        homeItemClickListener?.openLibraryDetailFragment(library)
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
                val resourceId = library.id
                if (resourceId != null) {
                    val tags = tagCache[resourceId].orEmpty()
                    renderTagCloud(holder.rowLibraryBinding.flexboxDrawable, tags)
                    handled = true
                }
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

    private fun displayTagCloud(holder: ViewHolderLibrary, position: Int) {
        val flexboxDrawable = holder.rowLibraryBinding.flexboxDrawable
        val resourceId = libraryList.getOrNull(position)?.id
        if (resourceId == null) {
            flexboxDrawable.removeAllViews()
            return
        }

        val cachedTags = tagCache[resourceId]
        if (cachedTags != null) {
            renderTagCloud(flexboxDrawable, cachedTags)
            return
        }

        flexboxDrawable.removeAllViews()

        val lifecycleOwner = context as? LifecycleOwner ?: return
        if (!tagRequestsInProgress.add(resourceId)) {
            return
        }
        lifecycleOwner.lifecycleScope.launch {
            try {
                val tags = withContext(Dispatchers.IO) {
                    tagRepository.getTagsForResource(resourceId)
                }
                tagCache[resourceId] = tags

                if (isActive) {
                    val adapterPosition = holder.bindingAdapterPosition
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val currentResourceId = libraryList.getOrNull(adapterPosition)?.id
                        if (currentResourceId == resourceId) {
                            renderTagCloud(holder.rowLibraryBinding.flexboxDrawable, tags)
                        }
                    }
                }
            } finally {
                tagRequestsInProgress.remove(resourceId)
            }
        }
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

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        updateList(sortLibraryListByTitle())
    }

    fun toggleSortOrder() {
        isAscending = !isAscending
        updateList(sortLibraryList())
    }

    private fun sortLibraryListByTitle(): List<RealmMyLibrary?> {
        return if (isTitleAscending) {
            libraryList.sortedBy { it?.title?.lowercase(Locale.ROOT) }
        } else {
            libraryList.sortedByDescending { it?.title?.lowercase(Locale.ROOT) }
        }
    }

    private fun sortLibraryList(): List<RealmMyLibrary?> {
        return if (isAscending) {
            libraryList.sortedBy { it?.createdDate }
        } else {
            libraryList.sortedByDescending { it?.createdDate }
        }
    }

    override fun getItemCount(): Int {
        return libraryList.size
    }

    private fun updateList(newList: List<RealmMyLibrary?>) {
        diffJob?.cancel()
        diffJob = (context as? LifecycleOwner)?.lifecycleScope?.launch {
            val diffResult = withContext(Dispatchers.Default) {
                DiffUtils.calculateDiff(
                    libraryList,
                    newList,
                    areItemsTheSame = { old, new -> old?.id == new?.id },
                    areContentsTheSame = { old, new ->
                        old?.title == new?.title &&
                                old?.description == new?.description &&
                                old?.createdDate == new?.createdDate &&
                                old?.averageRating == new?.averageRating &&
                                old?.timesRated == new?.timesRated
                    },
                    getChangePayload = { old, new ->
                        val ratingChanged = old?.averageRating != new?.averageRating || old?.timesRated != new?.timesRated
                        val otherContentChanged = old?.title != new?.title ||
                                old?.description != new?.description ||
                                old?.createdDate != new?.createdDate

                        if (ratingChanged && !otherContentChanged) {
                            RATING_PAYLOAD
                        } else {
                            null
                        }
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
            val index = libraryList.indexOfFirst { it?.resourceId == resourceId }
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

    internal inner class ViewHolderLibrary(val rowLibraryBinding: RowLibraryBinding) :
        RecyclerView.ViewHolder(rowLibraryBinding.root) {
            init {
                rowLibraryBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val adapterPosition = bindingAdapterPosition
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            val library = libraryList.getOrNull(adapterPosition)
                            if (userModel?.isGuest() == false) {
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
