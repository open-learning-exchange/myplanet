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
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import io.realm.Realm
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowLibraryBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.SelectionUtils
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities

class AdapterResource(
    private val context: Context,
    libraryList: List<RealmMyLibrary?>,
    private var ratingMap: HashMap<String?, JsonObject>,
    private val realm: Realm
) : ListAdapter<RealmMyLibrary?, RecyclerView.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old?.id == new?.id },
        areContentsTheSame = { old, new ->
            old?.title == new?.title &&
                old?.description == new?.description &&
                old?.createdDate == new?.createdDate &&
                old?.averageRating == new?.averageRating &&
                old?.timesRated == new?.timesRated
        }
    )
) {
    private val selectedItems: MutableList<RealmMyLibrary?> = ArrayList()
    private var listener: OnLibraryItemSelected? = null
    private val config: ChipCloudConfig = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private var isAscending = true
    private var isTitleAscending = false
    var userModel: RealmUserModel ?= null

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
        submitList(libraryList)
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun getLibraryList(): List<RealmMyLibrary?> {
        return currentList
    }

    fun setLibraryList(libraryList: List<RealmMyLibrary?>) {
        submitList(libraryList)
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
            val item = getItem(position)
            holder.bind()
            holder.rowLibraryBinding.title.text = item?.title
            setMarkdownText(holder.rowLibraryBinding.description, item?.description!!)
            holder.rowLibraryBinding.description.setOnClickListener {
                openLibrary(item)
            }
            holder.rowLibraryBinding.timesRated.text = context.getString(R.string.num_total, item?.timesRated)
            holder.rowLibraryBinding.checkbox.isChecked = selectedItems.contains(item)
            holder.rowLibraryBinding.checkbox.contentDescription = "${context.getString(R.string.selected)} ${item?.title}"
            holder.rowLibraryBinding.rating.text =
                if (TextUtils.isEmpty(item?.averageRating)) {
                    "0.0"
                } else {
                    String.format(Locale.getDefault(), "%.1f", item?.averageRating?.toDouble())
                }
            holder.rowLibraryBinding.tvDate.text = item?.createdDate?.let { formatDate(it, "MMM dd, yyyy") }
            displayTagCloud(holder.rowLibraryBinding.flexboxDrawable, position)
            holder.itemView.setOnClickListener { openLibrary(item) }
            userModel = UserProfileDbHandler(context).userModel
            if (item?.isResourceOffline() == true) {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.INVISIBLE
            } else {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.VISIBLE
            }
            holder.rowLibraryBinding.ivDownloaded.contentDescription =
                if (item?.isResourceOffline() == true) {
                    context.getString(R.string.view)
                } else {
                    context.getString(R.string.download)
                }
            if (ratingMap.containsKey(item?.resourceId)) {
                val `object` = ratingMap[item?.resourceId]
                CourseRatingUtils.showRating(
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
                    holder.rowLibraryBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, item?.title)
                    SelectionUtils.handleCheck((view as CheckBox).isChecked, position, selectedItems, currentList)
                    if (listener != null) listener?.onSelectedListChange(selectedItems)
                }
            } else {
                holder.rowLibraryBinding.checkbox.visibility = View.GONE
            }
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
        val tags: List<RealmTag> = realm.where(RealmTag::class.java).equalTo("db", "resources").equalTo("linkId", getItem(position)?.id).findAll()
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
        submitList(sortLibraryListByTitle())
    }

    fun toggleSortOrder() {
        isAscending = !isAscending
        submitList(sortLibraryList())
    }

    private fun sortLibraryListByTitle(): List<RealmMyLibrary?> {
        return if (isTitleAscending) {
            currentList.sortedBy { it?.title?.lowercase(Locale.ROOT) }
        } else {
            currentList.sortedByDescending { it?.title?.lowercase(Locale.ROOT) }
        }
    }

    private fun sortLibraryList(): List<RealmMyLibrary?> {
        return if (isAscending) {
            currentList.sortedBy { it?.createdDate }
        } else {
            currentList.sortedByDescending { it?.createdDate }
        }
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
                            homeItemClickListener?.showRatingDialog(
                                "resource",
                                getItem(bindingAdapterPosition)?.resourceId,
                                getItem(bindingAdapterPosition)?.title,
                                ratingChangeListener
                            )
                        }
                    }
                    true
                }
            }

        fun bind() {}
    }
}
