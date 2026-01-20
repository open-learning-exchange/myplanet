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
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnCourseItemSelected
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getInt
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities

class CoursesAdapter(
    private val context: Context,
    private var userModel: RealmUserModel?,
    private val tagsRepository: TagsRepository
) : ListAdapter<CourseListItem, RecyclerView.ViewHolder>(CourseDiffCallback) {
    private val selectedItems: MutableList<RealmMyCourse?> = ArrayList()
    private var listener: OnCourseItemSelected? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private val config: ChipCloudConfig
    private var isAscending = true
    private var isTitleAscending = false
    private var areAllSelected = false
    private val tagCache: MutableMap<String, List<RealmTag>> = mutableMapOf()
    private val activeJobs: MutableMap<String, Job> = mutableMapOf()

    companion object {
        const val TAG_PAYLOAD = "payload_tags"
        const val RATING_PAYLOAD = "payload_rating"
        const val PROGRESS_PAYLOAD = "payload_progress"
    }

    object CourseDiffCallback : DiffUtil.ItemCallback<CourseListItem>() {
        override fun areItemsTheSame(oldItem: CourseListItem, newItem: CourseListItem): Boolean {
            return oldItem.course.id == newItem.course.id
        }

        override fun areContentsTheSame(oldItem: CourseListItem, newItem: CourseListItem): Boolean {
            val oldCourse = oldItem.course
            val newCourse = newItem.course

            val courseSame = oldCourse.courseTitle == newCourse.courseTitle &&
                    oldCourse.description == newCourse.description &&
                    oldCourse.gradeLevel == newCourse.gradeLevel &&
                    oldCourse.subjectLevel == newCourse.subjectLevel &&
                    oldCourse.createdDate == newCourse.createdDate &&
                    oldCourse.isMyCourse == newCourse.isMyCourse &&
                    oldCourse.getNumberOfSteps() == newCourse.getNumberOfSteps()

            val ratingSame = oldItem.rating == newItem.rating
            val progressSame = oldItem.progress == newItem.progress

            return courseSame && ratingSame && progressSame
        }

        override fun getChangePayload(oldItem: CourseListItem, newItem: CourseListItem): Any? {
            val bundle = Bundle()
            if (oldItem.rating != newItem.rating) {
                bundle.putBoolean(RATING_PAYLOAD, true)
            }
            if (oldItem.progress != newItem.progress) {
                bundle.putBoolean(PROGRESS_PAYLOAD, true)
            }
            return if (bundle.isEmpty) null else bundle
        }
    }

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
        config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun getCourseList(): List<RealmMyCourse?> {
        return currentList.map { it.course }
    }

    private fun sortCourseListByTitle(list: List<CourseListItem>): List<CourseListItem> {
        return list.sortedWith { item1, item2 ->
            val course1 = item1.course
            val course2 = item2.course
            if (isTitleAscending) {
                course1.courseTitle?.compareTo(course2.courseTitle ?: "", ignoreCase = true) ?: 0
            } else {
                course2.courseTitle?.compareTo(course1.courseTitle ?: "", ignoreCase = true) ?: 0
            }
        }
    }

    private fun sortCourseList(list: List<CourseListItem>): List<CourseListItem> {
        return list.sortedWith { item1, item2 ->
            val course1 = item1.course
            val course2 = item2.course
            if (isAscending) {
                course1.createdDate.compareTo(course2.createdDate)
            } else {
                course2.createdDate.compareTo(course1.createdDate)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is CoursesViewHolder) {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < itemCount) {
                val item = getItem(position)
                item?.course?.id?.let { courseId ->
                    activeJobs[courseId]?.cancel()
                    activeJobs.remove(courseId)
                }
            }
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<CourseListItem>, currentList: MutableList<CourseListItem>) {
        super.onCurrentListChanged(previousList, currentList)
        val currentIds = currentList.mapNotNull { it.course.id }.toSet()
        val previousIds = previousList.mapNotNull { it.course.id }.toSet()

        val removedIds = previousIds - currentIds
        removedIds.forEach { id ->
            activeJobs[id]?.cancel()
            activeJobs.remove(id)
            tagCache.remove(id)
        }
    }

    fun toggleTitleSortOrder(onComplete: (() -> Unit)? = null) {
        isTitleAscending = !isTitleAscending
        val sortedList = sortCourseListByTitle(currentList)
        submitList(sortedList) {
             onComplete?.invoke()
        }
    }

    fun toggleSortOrder(onComplete: (() -> Unit)? = null) {
        isAscending = !isAscending
        val sortedList = sortCourseList(currentList)
        submitList(sortedList) {
             onComplete?.invoke()
        }
    }

    fun setListener(listener: OnCourseItemSelected?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CoursesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is CoursesViewHolder) return

        holder.bind(position)
        val item = getItem(position) ?: return
        val course = item.course

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
        displayTagCloud(holder, position)

        val isGuest = userModel?.isGuest() ?: true
        if (!isGuest) setupRatingBar(holder, course)
        setupCheckbox(holder, course, position, isGuest)

        updateRatingViews(holder, position)
        updateProgressViews(holder, position)

        holder.rowCourseBinding.root.setOnClickListener {
            val newPosition = holder.bindingAdapterPosition
            if (newPosition != RecyclerView.NO_POSITION) {
                openCourse(getItem(newPosition).course, 0)
            }
        }
    }

    private fun updateVisibilityForMyCourse(holder: CoursesViewHolder, course: RealmMyCourse) {
        if (course.isMyCourse) {
            holder.rowCourseBinding.isMyCourse.visibility = View.VISIBLE
            holder.rowCourseBinding.checkbox.visibility = View.GONE
        } else {
            holder.rowCourseBinding.isMyCourse.visibility = View.GONE
            holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
        }
    }

    private fun configureDescription(holder: CoursesViewHolder, course: RealmMyCourse, position: Int) {
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

    private fun configureDateViews(holder: CoursesViewHolder, course: RealmMyCourse) {
        if (course.gradeLevel.isNullOrEmpty() && course.subjectLevel.isNullOrEmpty()) {
            holder.rowCourseBinding.holder.visibility = View.VISIBLE
            holder.rowCourseBinding.tvDate2.visibility = View.VISIBLE
            holder.rowCourseBinding.tvDate.visibility = View.GONE
            try {
                holder.rowCourseBinding.tvDate2.text = formatDate(course.createdDate, "MMM dd, yyyy")
            } catch (e: Exception) {
                // Should handle exception properly or ignore as in original code
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

    private fun setupRatingBar(holder: CoursesViewHolder, course: RealmMyCourse) {
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

    private fun setupCheckbox(holder: CoursesViewHolder, course: RealmMyCourse, position: Int, isGuest: Boolean) {
        if (!isGuest) {
            if (course.isMyCourse) {
                holder.rowCourseBinding.checkbox.visibility = View.GONE
            } else {
                holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
                holder.rowCourseBinding.checkbox.isChecked = selectedItems.contains(course)
                holder.rowCourseBinding.checkbox.setOnClickListener { view: View ->
                    holder.rowCourseBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, course.courseTitle)

                    if ((view as CheckBox).isChecked) {
                        selectedItems.add(course)
                    } else {
                        selectedItems.remove(course)
                    }

                    listener?.onSelectedListChange(selectedItems)
                }
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

    fun cancelAllJobs() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun areAllSelected(): Boolean {
        val selectableCourses = currentList.map { it.course }.filter { !it.isMyCourse }
        areAllSelected = selectedItems.size == selectableCourses.size && selectableCourses.isNotEmpty()
        return areAllSelected
    }

    fun selectAllItems(selectAll: Boolean) {
        selectedItems.clear()

        if (selectAll) {
            val selectableCourses = currentList.map { it.course }.filter { !it.isMyCourse }
            selectedItems.addAll(selectableCourses)
        }

        currentList.forEachIndexed { index, item ->
            if (!item.course.isMyCourse) {
                notifyItemChanged(index)
            }
        }

        listener?.onSelectedListChange(selectedItems)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (holder !is CoursesViewHolder) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val hasTagPayload = payloads.any { it == TAG_PAYLOAD }
        val bundle = payloads.filterIsInstance<Bundle>().fold(Bundle()) { acc, b -> acc.apply { putAll(b) } }
        val hasRatingPayload = bundle.containsKey(RATING_PAYLOAD)
        val hasProgressPayload = bundle.containsKey(PROGRESS_PAYLOAD)

        if (hasTagPayload || hasRatingPayload || hasProgressPayload) {
            if (hasTagPayload) {
                val courseId = getItem(position).course.id
                val tags = tagCache[courseId].orEmpty()
                renderTagCloud(holder.rowCourseBinding.flexboxDrawable, tags)
            }
            if (hasRatingPayload) {
                updateRatingViews(holder, position)
            }
            if (hasProgressPayload) {
                updateProgressViews(holder, position)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun displayTagCloud(holder: CoursesViewHolder, position: Int) {
        val flexboxDrawable = holder.rowCourseBinding.flexboxDrawable
        val courseId = getItem(position).course.id ?: run {
             flexboxDrawable.removeAllViews()
             return
        }

        val cachedTags = tagCache[courseId]
        if (cachedTags != null) {
            renderTagCloud(flexboxDrawable, cachedTags)
            return
        }

        flexboxDrawable.removeAllViews()

        activeJobs[courseId]?.cancel()

        val job = holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            try {
                val tags = tagsRepository.getTagsForCourse(courseId)
                tagCache[courseId] = tags
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        notifyItemChanged(adapterPosition, TAG_PAYLOAD)
                    }
                }
            } finally {
                activeJobs.remove(courseId)
            }
        }
        if (job != null) {
            activeJobs[courseId] = job
        }
    }

    private fun renderTagCloud(flexboxDrawable: FlexboxLayout, tags: List<RealmTag>) {
        flexboxDrawable.removeAllViews()
        if (tags.isEmpty()) {
            return
        }
        val chipCloud = ChipCloud(context, flexboxDrawable, config)
        tags.forEach { tag ->
            chipCloud.addChip(tag.name ?: "")
        }
        chipCloud.setListener { index: Int, _: Boolean, isSelected: Boolean ->
            if (isSelected) {
                tags.getOrNull(index)?.let { selectedTag ->
                    listener?.onTagClicked(selectedTag)
                }
            }
        }
    }

    private fun updateRatingViews(holder: CoursesViewHolder, position: Int) {
        val item = getItem(position) ?: return
        if (item.rating != null) {
            CourseRatingUtils.showRating(
                context,
                item.rating,
                holder.rowCourseBinding.rating,
                holder.rowCourseBinding.timesRated,
                holder.rowCourseBinding.ratingBar
            )
        } else {
            holder.rowCourseBinding.ratingBar.rating = 0f
            holder.rowCourseBinding.rating.text = context.getString(R.string.zero_point_zero)
            holder.rowCourseBinding.timesRated.text = context.getString(R.string.rating_count_format, 0)
        }
    }

    private fun updateProgressViews(holder: CoursesViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val progress = item.progress
        if (progress != null) {
            holder.rowCourseBinding.courseProgress.max = getInt("max", progress)
            val currentProgress = getInt("current", progress)
            holder.rowCourseBinding.courseProgress.progress = currentProgress
            if (currentProgress < holder.rowCourseBinding.courseProgress.max) {
                holder.rowCourseBinding.courseProgress.secondaryProgress = currentProgress + 1
            }
            holder.rowCourseBinding.courseProgress.visibility = View.VISIBLE
        } else {
            holder.rowCourseBinding.courseProgress.visibility = View.GONE
        }
    }

    private fun openCourse(realmMyCourses: RealmMyCourse?, step: Int) {
        if (homeItemClickListener != null) {
            val f = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", realmMyCourses?.courseId)
            b.putInt("position", step)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        cancelAllJobs()
    }

    internal inner class CoursesViewHolder(val rowCourseBinding: RowCourseBinding) :
        RecyclerView.ViewHolder(rowCourseBinding.root) {
        private var adapterPosition = 0

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    openCourse(getItem(adapterPosition).course, 0)
                }
            }
            rowCourseBinding.courseProgress.scaleY = 0.3f
            rowCourseBinding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < itemCount) {
                        val item = getItem(position)
                        if (item?.progress != null) {
                            val current = getInt("current", item.progress)
                            if (b && i <= current + 1) {
                                openCourse(item.course, seekBar.progress)
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
}
