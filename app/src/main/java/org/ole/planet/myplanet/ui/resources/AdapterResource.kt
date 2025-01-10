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
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.courses.AdapterCourses
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Locale

class AdapterResource(private val context: Context, private var libraryList: List<RealmMyLibrary?>, private val ratingMap: HashMap<String?, JsonObject>, private val realm: Realm) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val rowLibraryBinding =
            RowLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderLibrary(rowLibraryBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderLibrary) {
            holder.bind()
            holder.rowLibraryBinding.title.text = libraryList[position]?.title
            setMarkdownText(holder.rowLibraryBinding.description, libraryList[position]?.description!!)
            holder.rowLibraryBinding.description.setOnClickListener {
                val library = libraryList[position]
                openLibrary(library)
            }
            holder.rowLibraryBinding.timesRated.text = context.getString(R.string.num_total, libraryList[position]?.timesRated)
            holder.rowLibraryBinding.checkbox.isChecked = selectedItems.contains(libraryList[position])
            holder.rowLibraryBinding.checkbox.contentDescription = "${context.getString(R.string.selected)} ${libraryList[position]?.title}"
            holder.rowLibraryBinding.rating.text =
                if (TextUtils.isEmpty(libraryList[position]?.averageRating)) {
                    "0.0"
                } else {
                    String.format(Locale.getDefault(), "%.1f", libraryList[position]?.averageRating?.toDouble())
                }
            holder.rowLibraryBinding.tvDate.text = libraryList[position]?.createdDate?.let { formatDate(it, "MMM dd, yyyy") }
            displayTagCloud(holder.rowLibraryBinding.flexboxDrawable, position)
            holder.itemView.setOnClickListener { openLibrary(libraryList[position]) }
            userModel = UserProfileDbHandler(context).userModel
            if (libraryList[position]?.isResourceOffline() == true) {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.INVISIBLE
            } else {
                holder.rowLibraryBinding.ivDownloaded.visibility = View.VISIBLE
            }
            holder.rowLibraryBinding.ivDownloaded.contentDescription =
                if (libraryList[position]?.isResourceOffline() == true) {
                    context.getString(R.string.view)
                } else {
                    context.getString(R.string.download)
                }
            if (ratingMap.containsKey(libraryList[position]?.resourceId)) {
                val `object` = ratingMap[libraryList[position]?.resourceId]
                AdapterCourses.showRating(`object`, holder.rowLibraryBinding.rating, holder.rowLibraryBinding.timesRated, holder.rowLibraryBinding.ratingBar)
            } else {
                holder.rowLibraryBinding.ratingBar.rating = 0f
            }

            if (userModel?.isGuest() == false) {
                holder.rowLibraryBinding.checkbox.setOnClickListener { view: View ->
                    holder.rowLibraryBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, libraryList[position]?.title)
                    Utilities.handleCheck((view as CheckBox).isChecked, position, selectedItems, libraryList)
                    if (listener != null) listener?.onSelectedListChange(selectedItems)
                }
            }
            else{
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

    private fun displayTagCloud(flexboxDrawable: FlexboxLayout, position: Int) {
        flexboxDrawable.removeAllViews()
        val chipCloud = ChipCloud(context, flexboxDrawable, config)
        val tags: List<RealmTag> = realm.where(RealmTag::class.java).equalTo("db", "resources").equalTo("linkId", libraryList[position]?.id).findAll()
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

    override fun getItemCount(): Int {
        return libraryList.size
    }

    internal inner class ViewHolderLibrary(val rowLibraryBinding: RowLibraryBinding) :
        RecyclerView.ViewHolder(rowLibraryBinding.root) {
            init {
                rowLibraryBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (userModel?.isGuest() == false) {
                            homeItemClickListener?.showRatingDialog("resource",
                                libraryList[bindingAdapterPosition]?.resourceId,
                                libraryList[bindingAdapterPosition]?.title,
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
