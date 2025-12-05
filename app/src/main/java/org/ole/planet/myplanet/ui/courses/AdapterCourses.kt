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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnCourseItemSelected
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowCourseBinding
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.dto.CourseItem
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.Utilities

class AdapterCourses(
    private val context: Context,
    private val tagRepository: TagRepository,
    private val lifecycleOwner: LifecycleOwner
) : ListAdapter<CourseItem, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<CourseItem>() {
    override fun areItemsTheSame(oldItem: CourseItem, newItem: CourseItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CourseItem, newItem: CourseItem): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: CourseItem, newItem: CourseItem): Any? {
        val bundle = Bundle()
        if (oldItem.rating != newItem.rating || oldItem.timesRated != newItem.timesRated) {
            bundle.putBoolean(RATING_PAYLOAD, true)
        }
        if (oldItem.progress != newItem.progress || oldItem.progressMax != newItem.progressMax) {
            bundle.putBoolean(PROGRESS_PAYLOAD, true)
        }
        if (bundle.isEmpty) return null
        return bundle
    }
}) {
    private val selectedItems: MutableList<CourseItem> = ArrayList()
    private var listener: OnCourseItemSelected? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private val config: ChipCloudConfig
    private var areAllSelected = false
    private val tagCache: MutableMap<String, List<RealmTag>> = mutableMapOf()
    private val tagRequestsInProgress: MutableSet<String> = mutableSetOf()

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

    fun setListener(listener: OnCourseItemSelected?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHoldercourse(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is ViewHoldercourse) return

        val course = getItem(position) ?: return
        holder.bind(course)

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
        holder.rowCourseBinding.courseProgress.max = course.numberOfSteps
        displayTagCloud(holder, position)

        if (!course.isGuest) setupRatingBar(holder, course)
        setupCheckbox(holder, course, position, course.isGuest)

        updateRatingViews(holder, course)
        updateProgressViews(holder, course)

        holder.rowCourseBinding.root.setOnClickListener {
            val newPosition = holder.bindingAdapterPosition
            if (newPosition != RecyclerView.NO_POSITION) {
                openCourse(getItem(newPosition), 0)
            }
        }
    }

    private fun updateVisibilityForMyCourse(holder: ViewHoldercourse, course: CourseItem) {
        if (course.isMyCourse) {
            holder.rowCourseBinding.isMyCourse.visibility = View.VISIBLE
            holder.rowCourseBinding.checkbox.visibility = View.GONE
        } else {
            holder.rowCourseBinding.isMyCourse.visibility = View.GONE
            holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
        }
    }

    private fun configureDescription(holder: ViewHoldercourse, course: CourseItem, position: Int) {
        holder.rowCourseBinding.description.apply {
            text = course.description // Assuming description is already processed in DTO
            setMarkdownText(this, course.description)

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

    private fun configureDateViews(holder: ViewHoldercourse, course: CourseItem) {
        if (course.gradeLevel.isNullOrEmpty() && course.subjectLevel.isNullOrEmpty()) {
            holder.rowCourseBinding.holder.visibility = View.VISIBLE
            holder.rowCourseBinding.tvDate2.visibility = View.VISIBLE
            holder.rowCourseBinding.tvDate.visibility = View.GONE
            holder.rowCourseBinding.tvDate2.text = course.date
        } else {
            holder.rowCourseBinding.tvDate.visibility = View.VISIBLE
            holder.rowCourseBinding.tvDate2.visibility = View.GONE
            holder.rowCourseBinding.holder.visibility = View.GONE
            holder.rowCourseBinding.tvDate.text = course.date
        }
    }

    private fun setupRatingBar(holder: ViewHoldercourse, course: CourseItem) {
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

    private fun setupCheckbox(holder: ViewHoldercourse, course: CourseItem, position: Int, isGuest: Boolean) {
        if (!isGuest) {
            if (course.isMyCourse) {
                holder.rowCourseBinding.checkbox.visibility = View.GONE
            } else {
                holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
                // Use find to check if the item is selected based on ID
                val isSelected = selectedItems.any { it.id == course.id }
                holder.rowCourseBinding.checkbox.isChecked = isSelected
                holder.rowCourseBinding.checkbox.setOnClickListener { view: View ->
                    holder.rowCourseBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, course.courseTitle)
                    val isChecked = (view as CheckBox).isChecked
                    if (isChecked) {
                         if (selectedItems.none { it.id == course.id }) {
                             selectedItems.add(course)
                         }
                    } else {
                        selectedItems.removeAll { it.id == course.id }
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

    fun areAllSelected(): Boolean {
        val selectableCourses = currentList.filter { !it.isMyCourse }
        // Ensure all selectable courses are in selectedItems (by ID)
        areAllSelected = selectableCourses.all { selectableItem ->
             selectedItems.any { it.id == selectableItem.id }
        } && selectableCourses.isNotEmpty()
        return areAllSelected
    }

    fun selectAllItems(selectAll: Boolean) {
        selectedItems.clear()

        if (selectAll) {
            val selectableCourses = currentList.filter { !it.isMyCourse }
            selectedItems.addAll(selectableCourses)
        }

        notifyDataSetChanged()

        listener?.onSelectedListChange(selectedItems)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (holder !is ViewHoldercourse) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val hasTagPayload = payloads.any { it == TAG_PAYLOAD }
        val bundle = payloads.filterIsInstance<Bundle>().fold(Bundle()) { acc, b -> acc.apply { putAll(b) } }
        val hasRatingPayload = bundle.containsKey(RATING_PAYLOAD)
        val hasProgressPayload = bundle.containsKey(PROGRESS_PAYLOAD)

        if (hasTagPayload || hasRatingPayload || hasProgressPayload) {
            if (hasTagPayload) {
                val courseId = getItem(position)?.id ?: return
                val tags = tagCache[courseId].orEmpty()
                renderTagCloud(holder.rowCourseBinding.flexboxDrawable, tags)
            }
            if (hasRatingPayload) {
                updateRatingViews(holder, getItem(position))
            }
            if (hasProgressPayload) {
                updateProgressViews(holder, getItem(position))
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun displayTagCloud(holder: ViewHoldercourse, position: Int) {
        val flexboxDrawable = holder.rowCourseBinding.flexboxDrawable
        val courseId = getItem(position)?.id
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

        if (!tagRequestsInProgress.add(courseId)) {
            return
        }

        lifecycleOwner.lifecycleScope.launch {
            try {
                val tags = tagRepository.getTagsForCourse(courseId)
                tagCache[courseId] = tags
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        notifyItemChanged(adapterPosition, TAG_PAYLOAD)
                    }
                }
            } finally {
                tagRequestsInProgress.remove(courseId)
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

    private fun updateRatingViews(holder: ViewHoldercourse, course: CourseItem) {
        holder.rowCourseBinding.ratingBar.rating = course.rating
        holder.rowCourseBinding.rating.text = String.format("%.1f", course.rating)
        holder.rowCourseBinding.timesRated.text = context.getString(R.string.rating_count_format, course.timesRated)
    }

    private fun updateProgressViews(holder: ViewHoldercourse, course: CourseItem) {
        if (course.progressMax > 0) {
            holder.rowCourseBinding.courseProgress.max = course.progressMax
            holder.rowCourseBinding.courseProgress.progress = course.progress
            if (course.progress < course.progressMax) {
                holder.rowCourseBinding.courseProgress.secondaryProgress = course.progress + 1
            }
            holder.rowCourseBinding.courseProgress.visibility = View.VISIBLE
        } else {
            holder.rowCourseBinding.courseProgress.visibility = View.GONE
        }
    }

    private fun openCourse(courseItem: CourseItem?, step: Int) {
        if (homeItemClickListener != null) {
            val f: Fragment = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", courseItem?.courseId)
            b.putInt("position", step)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    internal inner class ViewHoldercourse(val rowCourseBinding: RowCourseBinding) :
        RecyclerView.ViewHolder(rowCourseBinding.root) {

        fun bind(course: CourseItem) {
            itemView.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    openCourse(getItem(bindingAdapterPosition), 0)
                }
            }
            rowCourseBinding.courseProgress.scaleY = 0.3f
            rowCourseBinding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = getItem(position)
                        // Assuming current progress is part of the item, logic here might need adjustment if we want interactive scrubbing
                        // For now we just check if user is scrubbing forward?
                        val current = item.progress
                        if (b && i <= current + 1) {
                            openCourse(item, seekBar.progress)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
    }
}
