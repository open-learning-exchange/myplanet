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
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnCourseItemSelectedListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowCourseBinding
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.Tag
import org.ole.planet.myplanet.utils.CourseRatingUtils
import org.ole.planet.myplanet.utils.DiffUtils
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
    private val tagsProvider: suspend (String) -> List<Tag>,
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
) {
    private val selectedItems: MutableList<Course?> = ArrayList()
    private var listener: OnCourseItemSelectedListener? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var progressMap: HashMap<String?, JsonObject>? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private val config: ChipCloudConfig
    private var isAscending = true
    private var isTitleAscending = false
    private var areAllSelected = false
    private val tagCache: MutableMap<String, List<Tag>> = mutableMapOf()
    private val activeJobs: MutableMap<String, Job> = mutableMapOf()

    companion object {
        private const val TAG_PAYLOAD = "payload_tags"
        private const val RATING_PAYLOAD = "payload_rating"
        private const val PROGRESS_PAYLOAD = "payload_progress"
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

    fun removeCourses(courseIds: List<String>) {
        val updated = currentList.filter { it.courseId !in courseIds }
        submitList(updated)
    }

    fun updateData(
        newCourseList: List<Course>,
        newMap: HashMap<String?, JsonObject>,
        newProgressMap: HashMap<String?, JsonObject>?
    ) {
        this.map.clear()
        this.map.putAll(newMap)
        this.progressMap = newProgressMap
        submitList(newCourseList) {
            val bundle = Bundle()
            bundle.putBoolean(RATING_PAYLOAD, true)
            bundle.putBoolean(PROGRESS_PAYLOAD, true)
            notifyItemRangeChanged(0, itemCount, bundle)
        }
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
        this.progressMap = progressMap
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

    fun cancelAllJobs() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun areAllSelected(): Boolean {
        val selectableCourses = currentList.filter { isMyCourseLib || !it.isMyCourse }
        areAllSelected = selectedItems.size == selectableCourses.size && selectableCourses.isNotEmpty()
        return areAllSelected
    }

    fun selectAllItems(selectAll: Boolean) {
        selectedItems.clear()

        if (selectAll) {
            val selectableCourses = currentList.filter { isMyCourseLib || !it.isMyCourse }
            selectedItems.addAll(selectableCourses)
        }

        currentList.forEachIndexed { index, course ->
            if (isMyCourseLib || !course.isMyCourse) {
                notifyItemChanged(index)
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
        val bundle = payloads.filterIsInstance<Bundle>().fold(Bundle()) { acc, b -> acc.apply { putAll(b) } }
        val hasRatingPayload = bundle.containsKey(RATING_PAYLOAD)
        val hasProgressPayload = bundle.containsKey(PROGRESS_PAYLOAD)

        if (hasTagPayload || hasRatingPayload || hasProgressPayload) {
            val course = getItem(position) ?: return
            holder.bindPayloads(position, course, hasTagPayload, hasRatingPayload, hasProgressPayload)
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
        cancelAllJobs()
    }

    abstract inner class CoursesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(position: Int, course: Course)
        abstract fun bindPayloads(position: Int, course: Course, hasTagPayload: Boolean, hasRatingPayload: Boolean, hasProgressPayload: Boolean)

        open fun onRecycled() {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < itemCount) {
                val course = getItem(position)
                val courseId = course.courseId
                activeJobs[courseId]?.cancel()
                activeJobs.remove(courseId)
            }
        }
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
            setupCheckbox(course, position, isGuest)

            updateRatingViews(position)
            updateProgressViews(position)
        }

        override fun bindPayloads(position: Int, course: Course, hasTagPayload: Boolean, hasRatingPayload: Boolean, hasProgressPayload: Boolean) {
            if (hasTagPayload) {
                val tags = tagCache[course.courseId].orEmpty()
                renderTagCloud(rowCourseBinding.flexboxDrawable, tags)
            }
            if (hasRatingPayload) {
                updateRatingViews(position)
            }
            if (hasProgressPayload) {
                updateProgressViews(position)
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

        private fun setupCheckbox(course: Course, position: Int, isGuest: Boolean) {
            if (!isGuest) {
                val showCheckbox = isMyCourseLib || !course.isMyCourse
                if (showCheckbox) {
                    rowCourseBinding.checkbox.visibility = View.VISIBLE
                    rowCourseBinding.checkbox.isChecked = selectedItems.contains(course)
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

            val cachedTags = tagCache[courseId]
            if (cachedTags != null) {
                renderTagCloud(flexboxDrawable, cachedTags)
                return
            }

            flexboxDrawable.removeAllViews()

            activeJobs[courseId]?.cancel()

            val job = itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                try {
                    val tags = tagsProvider(courseId)
                    tagCache[courseId] = tags
                    val adapterPosition = bindingAdapterPosition
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
