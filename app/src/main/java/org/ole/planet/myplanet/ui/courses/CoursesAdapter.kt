package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnCourseItemSelectedListener
import org.ole.planet.myplanet.callback.OnDiffRefreshListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowCourseBinding
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.Tag
import org.ole.planet.myplanet.utils.CourseRatingUtils
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getInt
import org.ole.planet.myplanet.utils.MarkdownUtils.prependBaseUrlToImages
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText
import org.ole.planet.myplanet.utils.SelectionUtils
import org.ole.planet.myplanet.utils.TimeUtils.formatDate
import org.ole.planet.myplanet.utils.Utilities

class CoursesAdapter(
    private val context: Context,
    private val map: HashMap<String?, JsonObject>,
    private val isGuest: Boolean,
    var isMyCourseLib: Boolean = false
) : ListAdapter<Course, CoursesAdapter.CoursesViewHolder>(
    DiffUtils.itemCallback<Course>(
        areItemsTheSame = { old, new -> old.courseId == new.courseId },
        areContentsTheSame = { old, new ->
            old.courseTitle == new.courseTitle &&
                    old.description == new.description &&
                    old.gradeLevel == new.gradeLevel &&
                    old.subjectLevel == new.subjectLevel &&
                    old.createdDate == new.createdDate &&
                    old.isMyCourse == new.isMyCourse &&
                    old.numberOfSteps == new.numberOfSteps
        }
    )
), OnDiffRefreshListener {
    override fun refreshWithDiff() {
        submitList(currentList.toList())
    }

    override fun refreshWithDiff(id: String) {
        val index = currentList.indexOfFirst { it.courseId == id }
        if (index != -1) {
            val bundle = Bundle()
            bundle.putBoolean(RATING_PAYLOAD, true)
            notifyItemChanged(index, bundle)
            return
        }
        submitList(currentList.toList())
    }

    private val externalFilesBaseUrl = "file://${FileUtils.getExternalFilesDir(context)}/ole/"
    private val selectedItems: MutableList<Course?> = ArrayList()
    private var listener: OnCourseItemSelectedListener? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var progressMap: HashMap<String?, JsonObject>? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private val config: ChipCloudConfig
    private var isAscending = true
    private var isTitleAscending = false
    private var tagsMap: Map<String, List<Tag>> = emptyMap()
    private val courseIdToPosition = mutableMapOf<String, Int>()

    override fun onCurrentListChanged(
        previousList: MutableList<Course>,
        currentList: MutableList<Course>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        courseIdToPosition.clear()
        currentList.forEachIndexed { index, course ->
            courseIdToPosition[course.courseId] = index
        }
    }

    companion object {
        private const val TAG_PAYLOAD = "payload_tags"
        private const val RATING_PAYLOAD = "payload_rating"
        private const val PROGRESS_PAYLOAD = "payload_progress"
        private const val SELECTION_PAYLOAD = "payload_selection"
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

    fun setTagsMap(newTagsMap: Map<String, List<Tag>>) {
        val updatedCourseIds = mutableSetOf<String?>()

        newTagsMap.forEach { (courseId, newTags) ->
            if (tagsMap[courseId] != newTags) {
                updatedCourseIds.add(courseId)
            }
        }

        tagsMap.keys.filterNot { newTagsMap.containsKey(it) }.forEach { removedKey ->
            updatedCourseIds.add(removedKey)
        }

        tagsMap = newTagsMap

        updatedCourseIds.forEach { courseId ->
            if (courseId.isNullOrEmpty()) {
                return@forEach
            }
            val index = courseIdToPosition[courseId]
            if (index != null && index != -1) {
                notifyItemChanged(index, TAG_PAYLOAD)
            }
        }
    }

    fun removeCourses(courseIds: List<String>) {
        val updated = currentList.filter { it.courseId !in courseIds }
        submitList(updated)
    }

    private fun sortCourseListByTitle(list: List<Course>): List<Course> {
        return list.sortedWith { course1, course2 ->
            if (isTitleAscending) {
                course1.courseTitle.compareTo(course2.courseTitle, ignoreCase = true)
            } else {
                course2.courseTitle.compareTo(course1.courseTitle, ignoreCase = true)
            }
        }
    }

    override fun onViewRecycled(holder: CoursesViewHolder) {
        super.onViewRecycled(holder)
        holder.onRecycled()
    }

    private fun sortCourseList(list: List<Course>): List<Course> {
        return list.sortedWith { course1, course2 ->
            if (isAscending) {
                course1.createdDate.compareTo(course2.createdDate)
            } else {
                course2.createdDate.compareTo(course1.createdDate)
            }
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

    fun setProgressMap(progressMap: HashMap<String?, JsonObject>?) {
        val oldMap = this.progressMap
        if (oldMap == progressMap) return
        this.progressMap = progressMap
        for (index in currentList.indices) {
            val courseId = currentList[index].courseId
            if (oldMap?.get(courseId) != progressMap?.get(courseId)) {
                val bundle = Bundle()
                bundle.putBoolean(PROGRESS_PAYLOAD, true)
                notifyItemChanged(index, bundle)
            }
        }
    }

    fun setRatingMap(ratingMap: HashMap<String?, JsonObject>) {
        this.map.clear()
        this.map.putAll(ratingMap)
    }

    fun setListener(listener: OnCourseItemSelectedListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoursesViewHolder {
        val binding = RowCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CourseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CoursesViewHolder, position: Int) {
        val course = getItem(position) ?: return
        holder.bind(position, course)
    }

    fun areAllSelected(): Boolean {
        val selectableCourses = currentList.filter { isMyCourseLib || !it.isMyCourse }
        return selectedItems.size == selectableCourses.size && selectableCourses.isNotEmpty()
    }

    fun selectAllItems(selectAll: Boolean) {
        val oldSelectedIds = selectedItems.mapNotNull { it?.courseId }.toSet()
        selectedItems.clear()

        if (selectAll) {
            val selectableCourses = currentList.filter { isMyCourseLib || !it.isMyCourse }
            selectedItems.addAll(selectableCourses)
        }

        val newSelectedIds = selectedItems.mapNotNull { it?.courseId }.toSet()

        currentList.forEachIndexed { index, course ->
            val wasSelected = oldSelectedIds.contains(course.courseId)
            val isSelected = newSelectedIds.contains(course.courseId)
            if (wasSelected != isSelected) {
                notifyItemChanged(index, SELECTION_PAYLOAD)
            }
        }

        listener?.onSelectedListChange(selectedItems)
    }

    override fun onBindViewHolder(
        holder: CoursesViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val hasTagPayload = payloads.any { it == TAG_PAYLOAD }
        val hasSelectionPayload = payloads.any { it == SELECTION_PAYLOAD }
        val bundle = payloads.filterIsInstance<Bundle>().fold(Bundle()) { acc, b -> acc.apply { putAll(b) } }
        val hasRatingPayload = bundle.containsKey(RATING_PAYLOAD)
        val hasProgressPayload = bundle.containsKey(PROGRESS_PAYLOAD)

        if (hasTagPayload || hasRatingPayload || hasProgressPayload || hasSelectionPayload) {
            val course = getItem(position) ?: return
            holder.bindPayloads(position, course, hasTagPayload, hasRatingPayload, hasProgressPayload, hasSelectionPayload)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun openCourse(course: Course?, step: Int) {
        if (homeItemClickListener != null) {
            val f: Fragment = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", course?.courseId)
            b.putInt("position", step)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
    }

    abstract inner class CoursesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(position: Int, course: Course)
        abstract fun bindPayloads(position: Int, course: Course, hasTagPayload: Boolean, hasRatingPayload: Boolean, hasProgressPayload: Boolean, hasSelectionPayload: Boolean)

        open fun onRecycled() {}
    }

    internal inner class CourseViewHolder(val rowCourseBinding: RowCourseBinding) :
        CoursesViewHolder(rowCourseBinding.root) {

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    openCourse(getItem(position), 0)
                }
            }
            rowCourseBinding.courseProgress.scaleY = 0.3f
            rowCourseBinding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < itemCount) {
                        val course = getItem(position)
                        if (progressMap?.containsKey(course.courseId) == true) {
                            val ob = progressMap?.get(course.courseId)
                            val current = getInt("current", ob)
                            if (b && i <= current + 1) {
                                openCourse(course, seekBar.progress)
                            }
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        override fun bind(position: Int, course: Course) {
            updateVisibilityForMyCourse(course)
            rowCourseBinding.title.text = course.courseTitle
            configureDescription(course, position)
            configureDateViews(course)
            setTextViewContent(
                rowCourseBinding.gradLevel,
                course.gradeLevel,
                rowCourseBinding.gradLevel,
                context.getString(R.string.grade_level_colon)
            )
            setTextViewContent(
                rowCourseBinding.subjectLevel,
                course.subjectLevel,
                rowCourseBinding.subjectLevel,
                context.getString(R.string.subject_level_colon)
            )
            rowCourseBinding.courseProgress.max = course.numberOfSteps
            displayTagCloud(position)

            if (!isGuest) setupRatingBar(course)
            setupCheckbox(course, isGuest)

            updateRatingViews(position)
            updateProgressViews(position)
        }

        override fun bindPayloads(position: Int, course: Course, hasTagPayload: Boolean, hasRatingPayload: Boolean, hasProgressPayload: Boolean, hasSelectionPayload: Boolean) {
            if (hasTagPayload) {
                renderTagCloud(rowCourseBinding.flexboxDrawable, tagsMap[course.courseId].orEmpty())
            }
            if (hasRatingPayload) {
                updateRatingViews(position)
            }
            if (hasProgressPayload) {
                updateProgressViews(position)
            }
            if (hasSelectionPayload) {
                if (!isGuest && (isMyCourseLib || !course.isMyCourse)) {
                    rowCourseBinding.checkbox.isChecked = selectedItems.any { it?.courseId == course.courseId }
                }
            }
        }

        private fun updateVisibilityForMyCourse(course: Course) {
            if (isMyCourseLib) {
                rowCourseBinding.isMyCourse.visibility = View.GONE
                rowCourseBinding.checkbox.visibility = View.VISIBLE
            } else {
                if (course.isMyCourse) {
                    rowCourseBinding.isMyCourse.visibility = View.VISIBLE
                    rowCourseBinding.checkbox.visibility = View.GONE
                } else {
                    rowCourseBinding.isMyCourse.visibility = View.GONE
                    rowCourseBinding.checkbox.visibility = View.VISIBLE
                }
            }
        }

        private fun configureDescription(course: Course, position: Int) {
            rowCourseBinding.description.apply {
                text = course.description
                val markdownContentWithLocalPaths = prependBaseUrlToImages(
                    course.description,
                    externalFilesBaseUrl,
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

        private fun configureDateViews(course: Course) {
            if (course.gradeLevel.isEmpty() && course.subjectLevel.isEmpty()) {
                rowCourseBinding.holder.visibility = View.VISIBLE
                rowCourseBinding.tvDate2.visibility = View.VISIBLE
                rowCourseBinding.tvDate.visibility = View.GONE
                try {
                    rowCourseBinding.tvDate2.text = formatDate(course.createdDate, "MMM dd, yyyy")
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            } else {
                rowCourseBinding.tvDate.visibility = View.VISIBLE
                rowCourseBinding.tvDate2.visibility = View.GONE
                rowCourseBinding.holder.visibility = View.GONE
                try {
                    rowCourseBinding.tvDate.text = formatDate(course.createdDate, "MMM dd, yyyy")
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }

        private fun setupRatingBar(course: Course) {
            rowCourseBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                if (event.action == MotionEvent.ACTION_UP) homeItemClickListener?.showRatingDialog(
                    "course",
                    course.courseId,
                    course.courseTitle,
                    ratingChangeListener
                )
                true
            }
        }

        private fun setupCheckbox(course: Course, isGuest: Boolean) {
            if (!isGuest) {
                val showCheckbox = isMyCourseLib || !course.isMyCourse
                if (showCheckbox) {
                    rowCourseBinding.checkbox.visibility = View.VISIBLE
                    rowCourseBinding.checkbox.isChecked = selectedItems.any { it?.courseId == course.courseId }
                    rowCourseBinding.checkbox.setOnClickListener { view: View ->
                        rowCourseBinding.checkbox.contentDescription =
                            context.getString(R.string.select_res_course, course.courseTitle)
                        val adapterPosition = bindingAdapterPosition
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            SelectionUtils.handleCheck((view as CheckBox).isChecked, adapterPosition, selectedItems, currentList)
                            listener?.onSelectedListChange(selectedItems)
                        }
                    }
                } else {
                    rowCourseBinding.checkbox.visibility = View.GONE
                }
            } else {
                rowCourseBinding.checkbox.visibility = View.GONE
            }
        }

        private fun displayTagCloud(position: Int) {
            val flexboxDrawable = rowCourseBinding.flexboxDrawable
            val course = getItem(position)
            val courseId = course?.courseId
            if (courseId == null) {
                flexboxDrawable.removeAllViews()
                return
            }
            renderTagCloud(flexboxDrawable, tagsMap[courseId].orEmpty())
        }

        private fun renderTagCloud(flexboxDrawable: FlexboxLayout, tags: List<Tag>) {
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

        private fun updateRatingViews(position: Int) {
            val course = getItem(position) ?: return
            if (map.containsKey(course.courseId)) {
                val ratingObject = map[course.courseId]
                CourseRatingUtils.showRating(
                    context,
                    ratingObject,
                    rowCourseBinding.rating,
                    rowCourseBinding.timesRated,
                    rowCourseBinding.ratingBar
                )
            } else {
                rowCourseBinding.ratingBar.rating = 0f
                rowCourseBinding.rating.text = context.getString(R.string.zero_point_zero)
                rowCourseBinding.timesRated.text = context.getString(R.string.rating_count_format, 0)
            }
        }

        private fun updateProgressViews(position: Int) {
            val course = getItem(position) ?: return
            val progress = progressMap?.get(course.courseId)
            if (progress != null) {
                rowCourseBinding.courseProgress.max = getInt("max", progress)
                val currentProgress = getInt("current", progress)
                rowCourseBinding.courseProgress.progress = currentProgress
                if (currentProgress < rowCourseBinding.courseProgress.max) {
                    rowCourseBinding.courseProgress.secondaryProgress = currentProgress + 1
                }
                rowCourseBinding.courseProgress.visibility = View.VISIBLE
            } else {
                rowCourseBinding.courseProgress.visibility = View.GONE
            }
            val badge = rowCourseBinding.statusBadge
            val current = getInt("current", progress)
            val max = getInt("max", progress)
            val (statusText, statusColor) = when {
                progress == null -> Pair(context.getString(R.string.status_not_started), R.color.status_not_started)
                current >= max   -> Pair(context.getString(R.string.status_completed),   R.color.status_completed)
                current > 0      -> Pair(context.getString(R.string.status_in_progress), R.color.status_in_progress)
                else             -> Pair(context.getString(R.string.status_not_started), R.color.status_not_started)
            }
            badge.text = statusText
            badge.visibility = View.VISIBLE
            (badge.background as? GradientDrawable)
                ?.setColor(ContextCompat.getColor(context, statusColor))
        }

        private fun setTextViewContent(textView: TextView?, content: String?, layout: View?, prefix: String) {
            if (content.isNullOrEmpty()) {
                layout?.visibility = View.GONE
            } else {
                layout?.visibility = View.VISIBLE
                textView?.text = context.getString(R.string.prefix_content, prefix, content)
            }
        }

    }
}
