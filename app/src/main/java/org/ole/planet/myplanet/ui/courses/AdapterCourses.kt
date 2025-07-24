package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnCourseItemSelected
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getInt
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
class AdapterCourses(
    private val context: Context,
    currentList: List<RealmMyCourse?>,
    private val map: HashMap<String?, JsonObject>,
    private val userProfileDbHandler: UserProfileDbHandler
) : ListAdapter<RealmMyCourse?, RecyclerView.ViewHolder>(CourseDiffCallback()) {
    private val selectedItems: MutableList<RealmMyCourse?> = ArrayList()
    private var listener: OnCourseItemSelected? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var progressMap: HashMap<String?, JsonObject>? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private var mRealm: Realm? = null
    private val config: ChipCloudConfig
    private var isAscending = true
    private var isTitleAscending = false
    private var areAllSelected = false
    var userModel: RealmUserModel?= null

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
        config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)
        submitList(currentList)
    }

    fun setmRealm(mRealm: Realm?) {
        this.mRealm = mRealm
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun getCourseList(): List<RealmMyCourse?> {
        return currentList
    }

    fun setOriginalCourseList(currentList: List<RealmMyCourse?>){
        submitList(currentList)
    }

    fun setCourseList(currentList: List<RealmMyCourse?>) {
        submitList(currentList)
    }

    private fun sortCourseListByTitle(): List<RealmMyCourse?> {
        return if (isTitleAscending) {
            currentList.sortedBy { it?.courseTitle?.lowercase() }
        } else {
            currentList.sortedByDescending { it?.courseTitle?.lowercase() }
        }
    }

    private fun sortCourseList(): List<RealmMyCourse?> {
        return if (isAscending) {
            currentList.sortedBy { it?.createdDate }
        } else {
            currentList.sortedByDescending { it?.createdDate }
        }
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        submitList(sortCourseListByTitle())
    }

    fun toggleSortOrder() {
        isAscending = !isAscending
        submitList(sortCourseList())
    }

    fun setProgressMap(progressMap: HashMap<String?, JsonObject>?) {
        this.progressMap = progressMap
    }

    fun setListener(listener: OnCourseItemSelected?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHoldercourse(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is ViewHoldercourse) return

        holder.bind(position)
        val course = currentList[position] ?: return

        updateVisibilityForMyCourse(holder, course)
        holder.rowCourseBinding.title.text = course.courseTitle
        configureDescription(holder, course, position)
        configureDateViews(holder, course)
        setTextViewContent(
            holder.rowCourseBinding.gradLevel,
            course.gradeLevel,
            holder.rowCourseBinding.gradLevel,
            context.getString(R.string.grade_level_colon)
        )
        setTextViewContent(
            holder.rowCourseBinding.subjectLevel,
            course.subjectLevel,
            holder.rowCourseBinding.subjectLevel,
            context.getString(R.string.subject_level_colon)
        )
        holder.rowCourseBinding.courseProgress.max = course.getNumberOfSteps()
        displayTagCloud(holder.rowCourseBinding.flexboxDrawable, position)

        userModel = userProfileDbHandler.userModel
        val isGuest = userModel?.isGuest() ?: true
        if (!isGuest) setupRatingBar(holder, course)
        setupCheckbox(holder, course, position, isGuest)

        showProgressAndRating(position, holder)

        holder.rowCourseBinding.root.setOnClickListener {
            if (position != RecyclerView.NO_POSITION) {
                openCourse(currentList[position], 0)
            }
        }
    }

    private fun updateVisibilityForMyCourse(holder: ViewHoldercourse, course: RealmMyCourse) {
        if (course.isMyCourse) {
            holder.rowCourseBinding.isMyCourse.visibility = View.VISIBLE
            holder.rowCourseBinding.checkbox.visibility = View.GONE
        } else {
            holder.rowCourseBinding.isMyCourse.visibility = View.GONE
            holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
        }
    }

    private fun configureDescription(holder: ViewHoldercourse, course: RealmMyCourse, position: Int) {
        holder.rowCourseBinding.description.apply {
            text = course.description
            val markdownContentWithLocalPaths = prependBaseUrlToImages(
                course.description,
                "file://${context.getExternalFilesDir(null)}/ole/",
                150,
                100
            )
            setMarkdownText(this, markdownContentWithLocalPaths)

            setOnClickListener {
                homeItemClickListener?.openCallFragment(TakeCourseFragment().apply {
                    arguments = Bundle().apply {
                        putString("id", course.courseId)
                        putInt("position", position)
                    }
                })
            }
        }
    }

    private fun configureDateViews(holder: ViewHoldercourse, course: RealmMyCourse) {
        if (course.gradeLevel.isNullOrEmpty() && course.subjectLevel.isNullOrEmpty()) {
            holder.rowCourseBinding.holder.visibility = View.VISIBLE
            holder.rowCourseBinding.tvDate2.visibility = View.VISIBLE
            holder.rowCourseBinding.tvDate.visibility = View.GONE
            try {
                holder.rowCourseBinding.tvDate2.text = formatDate(course.createdDate, "MMM dd, yyyy")
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        } else {
            holder.rowCourseBinding.tvDate.visibility = View.VISIBLE
            holder.rowCourseBinding.tvDate2.visibility = View.GONE
            holder.rowCourseBinding.holder.visibility = View.GONE
            try {
                holder.rowCourseBinding.tvDate.text = formatDate(course.createdDate, "MMM dd, yyyy")
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private fun setupRatingBar(holder: ViewHoldercourse, course: RealmMyCourse) {
        holder.rowCourseBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_UP) homeItemClickListener?.showRatingDialog(
                "course",
                course.courseId,
                course.courseTitle,
                ratingChangeListener
            )
            true
        }
    }

    private fun setupCheckbox(holder: ViewHoldercourse, course: RealmMyCourse, position: Int, isGuest: Boolean) {
        if (!isGuest) {
            holder.rowCourseBinding.checkbox.isChecked = selectedItems.contains(course)
            holder.rowCourseBinding.checkbox.setOnClickListener { view: View ->
                holder.rowCourseBinding.checkbox.contentDescription =
                    context.getString(R.string.select_res_course, course.courseTitle)
                Utilities.handleCheck((view as CheckBox).isChecked, position, selectedItems, currentList)
                listener?.onSelectedListChange(selectedItems)
            }
        } else {
            holder.rowCourseBinding.checkbox.visibility = View.GONE
        }
    }

    private fun setTextViewContent(textView: TextView?, content: String?, layout: View?, prefix: String) {
        if (content.isNullOrEmpty()) {
            layout?.visibility = View.GONE
        } else {
            layout?.visibility = View.VISIBLE
            textView?.text = context.getString(R.string.prefix_content, prefix, content)
        }
    }

    fun areAllSelected(): Boolean {
        areAllSelected = selectedItems.size == currentList.size
        return areAllSelected
    }

    fun selectAllItems(selectAll: Boolean) {
        selectedItems.clear()
        if (selectAll) {
            selectedItems.addAll(currentList.filter { course ->
                course != null && !course.isMyCourse
            })
        }
        submitList(currentList.toList())
        listener?.onSelectedListChange(selectedItems)
    }

    private fun displayTagCloud(flexboxDrawable: FlexboxLayout, position: Int) {
        flexboxDrawable.removeAllViews()
        val chipCloud = ChipCloud(context, flexboxDrawable, config)
        val tags: List<RealmTag>? = mRealm?.where(RealmTag::class.java)?.equalTo("db", "courses")?.equalTo("linkId", currentList[position]!!.id)?.findAll()
        showTags(tags, chipCloud)
    }

    private fun showTags(tags: List<RealmTag>?, chipCloud: ChipCloud) {
        if (tags != null) {
            for (tag in tags) {
                val parent = mRealm?.where(RealmTag::class.java)?.equalTo("id", tag.tagId)?.findFirst()
                parent?.let { showChip(chipCloud, it) }
            }
        }
    }

    private fun showChip(chipCloud: ChipCloud, parent: RealmTag?) {
        chipCloud.addChip(if (parent != null) parent.name else "")
        chipCloud.setListener { _: Int, _: Boolean, b1: Boolean ->
            if (b1 && listener != null) {
                listener!!.onTagClicked(parent)
            }
        }
    }

    private fun showProgressAndRating(position: Int, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHoldercourse
        showProgress(viewHolder.rowCourseBinding, position)
        currentList[position]?.courseId?.let { courseId ->
            if (map.containsKey(courseId)) {
                val ratingObj = map[courseId]
                CourseRatingUtils.showRating(
                    ratingObj,
                    viewHolder.rowCourseBinding.rating,
                    viewHolder.rowCourseBinding.timesRated,
                    viewHolder.rowCourseBinding.ratingBar
                )
            } else {
                viewHolder.rowCourseBinding.ratingBar.rating = 0f
            }
        } ?: run {
            viewHolder.rowCourseBinding.ratingBar.rating = 0f
        }
    }

    private fun showProgress(binding: RowCourseBinding, position: Int) {
        val courseId = currentList[position]?.courseId
        if (courseId != null && progressMap?.containsKey(courseId) == true) {
            val ob = progressMap!![courseId]
            binding.courseProgress.max = getInt("max", ob)
            binding.courseProgress.progress = getInt("current", ob)
            if (getInt("current", ob) < getInt("max", ob)) {
                binding.courseProgress.secondaryProgress = getInt("current", ob) + 1
            }
            binding.courseProgress.visibility = View.VISIBLE
        } else {
            binding.courseProgress.visibility = View.GONE
        }
    }

    private fun openCourse(realmMyCourses: RealmMyCourse?, step: Int) {
        if (homeItemClickListener != null) {
            val f: Fragment = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", realmMyCourses?.courseId)
            b.putInt("position", step)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    fun updateCourseList(newCourseList: List<RealmMyCourse?>) {
        selectedItems.clear()
        submitList(newCourseList)
    }

    fun setRatingMap(newRatingMap: HashMap<String?, JsonObject>) {
        this.map.clear()
        this.map.putAll(newRatingMap)
        submitList(currentList.toList())
    }

    internal inner class ViewHoldercourse(val rowCourseBinding: RowCourseBinding) :
        RecyclerView.ViewHolder(rowCourseBinding.root) {
        private var adapterPosition = 0

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    openCourse(currentList[adapterPosition], 0)
                }
            }
            rowCourseBinding.courseProgress.scaleY = 0.3f
            rowCourseBinding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < currentList.size) {
                        currentList[bindingAdapterPosition]?.courseId?.let { courseId ->
                            if (progressMap?.containsKey(courseId) == true) {
                                val ob = progressMap!![courseId]
                                val current = getInt("current", ob)
                                if (b && i <= current + 1) {
                                    openCourse(currentList[bindingAdapterPosition], seekBar.progress)
                                }
                            }
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

    fun bind(position: Int) {
        adapterPosition = position
    }
}

class CourseDiffCallback : DiffUtil.ItemCallback<RealmMyCourse?>() {
    override fun areItemsTheSame(oldItem: RealmMyCourse?, newItem: RealmMyCourse?): Boolean {
        return oldItem?.id == newItem?.id
    }

    override fun areContentsTheSame(oldItem: RealmMyCourse?, newItem: RealmMyCourse?): Boolean {
        return oldItem == newItem
    }
}

}
