package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowLibraryBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.ui.courses.AdapterCourses
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Locale
import kotlin.math.*

class AdapterResource(private val context: Context, private var libraryList: List<RealmMyLibrary?>, private val ratingMap: HashMap<String?, JsonObject>, private val realm: Realm) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val selectedItems: MutableList<RealmMyLibrary?> = ArrayList()
    private var listener: OnLibraryItemSelected? = null
    private val config: ChipCloudConfig = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private var isAscending = true
    private var isTitleAscending = true
    private var areAllSelected = true

    private var _itemsPerPage: Int = 10
    var itemsPerPage: Int
        get() = _itemsPerPage
        set(value) {
            _itemsPerPage = value
            notifyDataSetChanged()
        }

    private var _currentPage: Int = 1
    var currentPage: Int
        get() = _currentPage
        set(value) {
            _currentPage = value
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
        this.libraryList = libraryList
        notifyDataSetChanged()
    }

    fun setListener(listener: OnLibraryItemSelected?) {
        this.listener = listener
    }

    fun getTotalResourceCount(): Int {
        return libraryList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val rowLibraryBinding = RowLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderLibrary(rowLibraryBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val paginatedResourceList = getPaginatedResourceList()
        if (holder is ViewHolderLibrary) {
            holder.bind()
            val resource = paginatedResourceList[position]
            holder.rowLibraryBinding.title.text = resource?.title
            setMarkdownText(holder.rowLibraryBinding.description, resource?.description!!)
            holder.rowLibraryBinding.timesRated.text = "${resource?.timesRated}${context.getString(R.string.total)}"
            holder.rowLibraryBinding.checkbox.isChecked = selectedItems.contains(resource)
            holder.rowLibraryBinding.checkbox.contentDescription = "${context.getString(R.string.selected)} ${resource.title}"
            holder.rowLibraryBinding.rating.text = if (TextUtils.isEmpty(resource.averageRating)) {
                "0.0"
            } else {
                String.format("%.1f", resource.averageRating?.toDouble())
            }
            holder.rowLibraryBinding.tvDate.text = formatDate(resource.createdDate, "MMM dd, yyyy")
            displayTagCloud(holder.rowLibraryBinding.flexboxDrawable, position)
            holder.itemView.setOnClickListener { openLibrary(resource) }
            holder.rowLibraryBinding.ivDownloaded.setImageResource(if (resource.isResourceOffline()) {
                R.drawable.ic_eye
            } else {
                R.drawable.ic_download
            })
            holder.rowLibraryBinding.ivDownloaded.contentDescription = if (resource.isResourceOffline()) {
                context.getString(R.string.view)
            } else {
                context.getString(R.string.download)
            }
            if (ratingMap.containsKey(resource.resourceId)) {
                val `object` = ratingMap[resource.resourceId]
                AdapterCourses.showRating(`object`, holder.rowLibraryBinding.rating, holder.rowLibraryBinding.timesRated, holder.rowLibraryBinding.ratingBar)
            } else {
                holder.rowLibraryBinding.ratingBar.rating = 0f
            }
            holder.rowLibraryBinding.checkbox.setOnClickListener { view: View ->
                holder.rowLibraryBinding.checkbox.contentDescription = context.getString(R.string.select_res_course, resource.title)
                Utilities.handleCheck((view as CheckBox).isChecked, position, selectedItems, libraryList)
                if (listener != null) listener?.onSelectedListChange(selectedItems)
            }
        }
    }

    fun areAllSelected(): Boolean {
        val paginatedResourceList = getPaginatedResourceList()
        areAllSelected = paginatedResourceList.all { selectedItems.contains(it) }
        return areAllSelected
    }

    fun selectAllItems(selectAll: Boolean) {
        val paginatedResourceList = getPaginatedResourceList()
        if (selectAll) {
            selectedItems.addAll(paginatedResourceList)
        } else {
            selectedItems.removeAll(paginatedResourceList)
        }
        notifyDataSetChanged()
        if (listener != null) {
            listener?.onSelectedListChange(selectedItems)
        }
    }

    private fun openLibrary(library: RealmMyLibrary?) {
        homeItemClickListener?.openLibraryDetailFragment(library)
    }

    private fun displayTagCloud(flexboxDrawable: FlexboxLayout, position: Int) {
        flexboxDrawable.removeAllViews()
        val chipCloud = ChipCloud(context, flexboxDrawable, config)
        val tags = realm.where(RealmTag::class.java).equalTo("db", "resources").equalTo("linkId", libraryList[position]?.id).findAll()
        for (tag in tags) {
            val parent = realm.where(RealmTag::class.java).equalTo("id", tag.tagId).findFirst()
            try {
                chipCloud.addChip(parent?.name)
            } catch (err: Exception) {
                chipCloud.addChip("--")
            }
            chipCloud.setListener { _: Int, _: Boolean, b1: Boolean ->
                if (b1 && listener != null) {
                    if (parent != null) {
                        listener?.onTagClicked(parent)
                    }
                }
            }
        }
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        sortLibraryListByTitle()
        notifyDataSetChanged()
    }

    fun toggleSortOrder() {
        isAscending = !isAscending
        sortLibraryList()
        notifyDataSetChanged()
    }

    private fun sortLibraryListByTitle() {
        libraryList = if (isTitleAscending) {
            libraryList.sortedBy { it?.title?.lowercase(Locale.ROOT) }
        } else {
            libraryList.sortedByDescending { it?.title?.lowercase(Locale.ROOT) }
        }
    }

    private fun sortLibraryList() {
        libraryList = if (isAscending) {
            libraryList.sortedBy { it?.createdDate }
        } else {
            libraryList.sortedByDescending { it?.createdDate }
        }
    }

    private fun getPaginatedResourceList(): List<RealmMyLibrary?> {
        return if (itemsPerPage == Int.MAX_VALUE) {
            libraryList
        } else {
            val startIndex = (currentPage - 1) * itemsPerPage
            val endIndex = min(startIndex + itemsPerPage, libraryList.size)
            libraryList.subList(startIndex, endIndex)
        }
    }

    override fun getItemCount(): Int {
        return if (itemsPerPage == Int.MAX_VALUE) {
            libraryList.size
        } else {
            getPaginatedResourceList().size
        }
    }

    internal inner class ViewHolderLibrary(val rowLibraryBinding: RowLibraryBinding) : RecyclerView.ViewHolder(rowLibraryBinding.root) {
        init {
            rowLibraryBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                if (event.action == MotionEvent.ACTION_UP) {
                    homeItemClickListener?.showRatingDialog("resource", libraryList[bindingAdapterPosition]?.resourceId, libraryList[bindingAdapterPosition]?.title, ratingChangeListener)
                }
                true
            }
        }

        fun bind() {}
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getTotalPages(): Int {
        return if (libraryList.isNotEmpty() && itemsPerPage != Int.MAX_VALUE) {
            ceil(libraryList.size.toDouble() / itemsPerPage).toInt()
        } else {
            1
        }
    }
}
