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
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    libraryList.getOrNull(adapterPosition)?.let { openLibrary(it) }
                }
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
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    libraryList.getOrNull(adapterPosition)?.let { openLibrary(it) }
                }
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
            if (ratingMap.containsKey(library.resourceId)) {
                val `object` = ratingMap[library.resourceId]
                CourseRatingUtils.showRating(
                    context,
                    `object`,
                    holder.rowLibraryBinding.rating,
                    holder.rowLibraryBinding.timesRated,
                    holder.rowLibraryBinding.ratingBar
                )
            } else {
                holder.rowLibraryBinding.ratingBar.rating = 0f
            }

            if (userModel?.isGuest() == false) {
                holder.rowLibraryBinding.checkbox.setOnClickListener { view: View ->
                    val adapterPosition = holder.bindingAdapterPosition
                    if (adapterPosition == RecyclerView.NO_POSITION) {
                        return@setOnClickListener
                    }
                    val currentLibrary = libraryList.getOrNull(adapterPosition) ?: return@setOnClickListener
                    holder.rowLibraryBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, currentLibrary.title ?: "")
                    val isChecked = (view as CheckBox).isChecked
                    if (isChecked) {
                        if (!selectedItems.contains(currentLibrary)) {
                            selectedItems.add(currentLibrary)
                        }
                    } else {
                        selectedItems.remove(currentLibrary)
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
        notifyDataSetChanged()
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
        if (holder is ViewHolderLibrary && payloads.contains(TAGS_PAYLOAD)) {
            val resourceId = libraryList.getOrNull(position)?.id ?: return
            val tags = tagCache[resourceId].orEmpty()
            renderTagCloud(holder.rowLibraryBinding.flexboxDrawable, tags)
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
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(adapterPosition, TAGS_PAYLOAD)
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
        val diffResult = DiffUtils.calculateDiff(
            libraryList,
            newList,
            areItemsTheSame = { old, new -> old?.id == new?.id },
            areContentsTheSame = { old, new ->
                old?.title == new?.title &&
                    old?.description == new?.description &&
                    old?.createdDate == new?.createdDate &&
                    old?.averageRating == new?.averageRating &&
                    old?.timesRated == new?.timesRated
            }
        )
        libraryList = newList
        diffResult.dispatchUpdatesTo(this)
    }

    fun setRatingMap(newRatingMap: HashMap<String?, JsonObject>) {
        this.ratingMap.clear()
        this.ratingMap.putAll(newRatingMap)
        notifyDataSetChanged()
    }

    internal inner class ViewHolderLibrary(val rowLibraryBinding: RowLibraryBinding) :
        RecyclerView.ViewHolder(rowLibraryBinding.root) {
            init {
                rowLibraryBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (userModel?.isGuest() == false) {
                            val adapterPosition = bindingAdapterPosition
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                val library = libraryList.getOrNull(adapterPosition)
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
