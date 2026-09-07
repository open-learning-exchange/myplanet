package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.signature.ObjectKey
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnCourseItemSelectedListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ItemCourseGridBinding
import org.ole.planet.myplanet.databinding.ItemCourseListBinding
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.CourseProgressState
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.utils.CourseSubject
import org.ole.planet.myplanet.utils.CourseSubjectClassifier
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ListViewMode
import org.ole.planet.myplanet.utils.SelectionUtils
import org.ole.planet.myplanet.utils.StableIdGenerator
import org.ole.planet.myplanet.utils.UrlUtils

class CoursesAdapter(
    private val context: Context,
    private var isGuest: Boolean,
    var isMyCourseLib: Boolean = false,
    private var viewMode: ListViewMode = ListViewMode.GRID
) : ListAdapter<Course, RecyclerView.ViewHolder>(
    DiffUtils.standardItemCallback<Course>(
        idSelector = { it.courseId },
        contentSelector = {
            listOf(
                it.courseTitle,
                it.description,
                it.gradeLevel,
                it.subjectLevel,
                it.createdDate,
                it.isMyCourse,
                it.numberOfSteps,
                it.coverFileName,
                it.courseRev
            )
        },
        payloadSelector = { old, new ->
            val payloads = mutableListOf<String>()
            if (old.isMyCourse != new.isMyCourse) payloads.add(PAYLOAD_SELECTION)
            if (old.numberOfSteps != new.numberOfSteps) payloads.add(PAYLOAD_PROGRESS)

            val unhandledChanges = old.courseTitle != new.courseTitle ||
                    old.description != new.description ||
                    old.gradeLevel != new.gradeLevel ||
                    old.subjectLevel != new.subjectLevel ||
                    old.createdDate != new.createdDate

            if (unhandledChanges) {
                null
            } else {
                if (payloads.isNotEmpty()) payloads else null
            }
        }
    )
) {
    fun notifyItemChangedById(id: String) {
        val index = currentList.indexOfFirst { it.courseId == id }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    private val selectedItems: MutableList<Course?> = ArrayList()
    private var listener: OnCourseItemSelectedListener? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var progressMap: Map<String, CourseProgressState>? = null

    companion object {
        const val PAYLOAD_PROGRESS = "payload_progress"
        const val PAYLOAD_SELECTION = "payload_selection"
        const val PAYLOAD_VIEW_MODE = "payload_view_mode"
        const val PAYLOAD_IDENTITY = "payload_identity"
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1

        private fun subjectColorRes(subject: CourseSubject): Int = when (subject) {
            CourseSubject.MATHEMATICS -> R.color.subject_math
            CourseSubject.LITERACY -> R.color.subject_literacy
            CourseSubject.HEALTH -> R.color.subject_health
            CourseSubject.SOCIAL_STUDIES -> R.color.subject_social_studies
            CourseSubject.TECHNOLOGY -> R.color.subject_technology
        }

        private fun subjectIconRes(subject: CourseSubject): Int = when (subject) {
            CourseSubject.MATHEMATICS -> R.drawable.ic_subject_math
            CourseSubject.LITERACY -> R.drawable.ic_type_book
            CourseSubject.HEALTH -> R.drawable.ic_subject_health
            CourseSubject.SOCIAL_STUDIES -> R.drawable.ic_subject_social
            CourseSubject.TECHNOLOGY -> R.drawable.ic_subject_technology
        }

        private fun subjectLabelRes(subject: CourseSubject): Int = when (subject) {
            CourseSubject.MATHEMATICS -> R.string.subject_label_mathematics
            CourseSubject.LITERACY -> R.string.subject_label_literacy
            CourseSubject.HEALTH -> R.string.subject_label_health
            CourseSubject.SOCIAL_STUDIES -> R.string.subject_label_social_studies
            CourseSubject.TECHNOLOGY -> R.string.subject_label_technology
        }
    }

    init {
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        val id = StableIdGenerator.generateStringId(item.courseId)
        return if (id != RecyclerView.NO_ID) id else StableIdGenerator.generateFallbackId(item)
    }

    fun setViewMode(mode: ListViewMode, onChanged: (() -> Unit)? = null) {
        if (viewMode != mode) {
            viewMode = mode
            notifyItemRangeChanged(0, itemCount, PAYLOAD_VIEW_MODE)
        }
        onChanged?.invoke()
    }

    fun updateIdentity(isGuest: Boolean) {
        if (this.isGuest != isGuest) {
            this.isGuest = isGuest
            notifyItemRangeChanged(0, itemCount, PAYLOAD_IDENTITY)
        }
    }

    fun removeCourses(courseIds: List<String>, onComplete: (() -> Unit)? = null) {
        val updated = currentList.filter { it.courseId !in courseIds }
        submitList(updated) {
            onComplete?.invoke()
        }
    }

    fun setProgressMap(progressMap: Map<String, CourseProgressState>?) {
        val oldMap = this.progressMap
        if (oldMap == progressMap) return
        this.progressMap = progressMap
        for (index in currentList.indices) {
            val courseId = currentList[index].courseId
            if (oldMap?.get(courseId) != progressMap?.get(courseId)) {
                notifyItemChanged(index, PAYLOAD_PROGRESS)
            }
        }
    }

    fun setListener(listener: OnCourseItemSelectedListener?) {
        this.listener = listener
    }

    override fun getItemViewType(position: Int): Int {
        return if (viewMode == ListViewMode.GRID) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GRID) {
            GridViewHolder(ItemCourseGridBinding.inflate(inflater, parent, false))
        } else {
            ListViewHolder(ItemCourseListBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val course = getItem(position) ?: return
        when (holder) {
            is GridViewHolder -> holder.bind(course)
            is ListViewHolder -> holder.bind(course)
        }
    }

    fun areAllSelected(): Boolean {
        val count = currentList.count { isMyCourseLib || !it.isMyCourse }
        return count > 0 && selectedItems.size == count
    }

    fun selectAllItems(selectAll: Boolean) {
        val oldSelectedIds = selectedItems.mapNotNull { it?.courseId }.toSet()
        selectedItems.clear()

        if (selectAll) {
            currentList.filterTo(selectedItems) { isMyCourseLib || !it.isMyCourse }
        }

        val newSelectedIds = selectedItems.mapNotNull { it?.courseId }.toSet()

        currentList.forEachIndexed { index, course ->
            val wasSelected = oldSelectedIds.contains(course.courseId)
            val isSelected = newSelectedIds.contains(course.courseId)
            if (wasSelected != isSelected) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }

        listener?.onSelectedListChange(selectedItems)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val flatPayloads = payloads.flatMap { if (it is List<*>) it else listOf(it) }
        val hasSelectionPayload = flatPayloads.any { it == PAYLOAD_SELECTION }
        val hasIdentityPayload = flatPayloads.any { it == PAYLOAD_IDENTITY }
        val hasProgressPayload = flatPayloads.any { it == PAYLOAD_PROGRESS } ||
                flatPayloads.filterIsInstance<Bundle>().any { it.containsKey(PAYLOAD_PROGRESS) }
        val hasViewModePayload = flatPayloads.any { it == PAYLOAD_VIEW_MODE }

        var partialHandled = false
        if (hasProgressPayload || hasSelectionPayload || hasIdentityPayload) {
            val course = getItem(position)
            if (course != null) {
                when (holder) {
                    is GridViewHolder -> holder.bindPayloads(course, hasProgressPayload, hasSelectionPayload, hasIdentityPayload)
                    is ListViewHolder -> holder.bindPayloads(course, hasProgressPayload, hasSelectionPayload, hasIdentityPayload)
                }
                partialHandled = true
            }
        }

        if (hasViewModePayload || !partialHandled) {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        val targetView = when (holder) {
            is GridViewHolder -> holder.binding.ivCover
            is ListViewHolder -> holder.binding.ivCover
            else -> null
        }
        targetView?.let { view ->
            Glide.with(context.applicationContext).clear(view)
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

    private fun setCoverColor(view: View, subject: CourseSubject) {
        val background = view.background?.mutate()
        if (background is GradientDrawable) {
            background.setColor(ContextCompat.getColor(context, subjectColorRes(subject)))
        }
    }

    private fun bindCover(
        course: Course,
        subject: CourseSubject,
        coverContainer: View,
        ivCover: ImageView,
        ivSubjectIcon: ImageView
    ) {
        setCoverColor(coverContainer, subject)
        val coverFile = MyCourse.getCoverImageFile(context, course.courseId, course.coverFileName)
        val model: Any? = if (coverFile?.exists() == true) {
            coverFile
        } else {
            UrlUtils.getCourseImageUrl(course.courseId, course.coverFileName)?.let { url ->
                GlideUrl(url, LazyHeaders.Builder().addHeader("Authorization", UrlUtils.header).build())
            }
        }
        if (model == null) {
            ivCover.visibility = View.GONE
            ivSubjectIcon.visibility = View.VISIBLE
            ivSubjectIcon.setImageResource(subjectIconRes(subject))
            return
        }
        ivSubjectIcon.visibility = View.GONE
        ivCover.visibility = View.VISIBLE
        Glide.with(context)
            .load(model)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(ObjectKey(course.courseRev.orEmpty()))
            .centerCrop()
            .error(R.drawable.ole_logo)
            .into(ivCover)
    }

    private fun buildMetaLine(course: Course): String {
        val parts = mutableListOf<String>()
        parts.add(context.getString(R.string.course_steps_count, course.numberOfSteps))
        course.gradeLevel.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }

    private fun updateVisibilityForMyCourse(course: Course, isMyCourseView: View, checkbox: CheckBox) {
        if (isMyCourseLib) {
            isMyCourseView.visibility = View.GONE
            checkbox.visibility = View.VISIBLE
        } else if (course.isMyCourse) {
            isMyCourseView.visibility = View.VISIBLE
            checkbox.visibility = View.GONE
        } else {
            isMyCourseView.visibility = View.GONE
            checkbox.visibility = View.VISIBLE
        }
    }

    private fun setupCheckbox(course: Course, checkbox: CheckBox, adapterPositionProvider: () -> Int) {
        if (isGuest) {
            checkbox.visibility = View.GONE
            checkbox.setOnClickListener(null)
            return
        }
        val showCheckbox = isMyCourseLib || !course.isMyCourse
        if (!showCheckbox) {
            checkbox.visibility = View.GONE
            checkbox.setOnClickListener(null)
            return
        }
        checkbox.visibility = View.VISIBLE
        checkbox.isChecked = selectedItems.any { it?.courseId == course.courseId }
        checkbox.setOnClickListener { view: View ->
            checkbox.contentDescription = context.getString(R.string.select_res_course, course.courseTitle)
            val adapterPosition = adapterPositionProvider()
            if (adapterPosition != RecyclerView.NO_POSITION) {
                SelectionUtils.handleCheck((view as CheckBox).isChecked, adapterPosition, selectedItems, currentList)
                listener?.onSelectedListChange(selectedItems)
            }
        }
    }

    private fun progressState(course: Course): Triple<Int, Int, Boolean> {
        val progress = progressMap?.get(course.courseId)
        val current = progress?.current ?: 0
        val max = progress?.max?.takeIf { it > 0 } ?: course.numberOfSteps
        val hasProgress = progress != null && course.isMyCourse
        return Triple(current, max, hasProgress)
    }

    private fun statusFor(current: Int, max: Int, hasProgress: Boolean): Triple<String, Int, Int> {
        return when {
            !hasProgress -> Triple(context.getString(R.string.status_not_started), R.drawable.bg_status_pill_not_started, R.color.list_status_not_started_text)
            current >= max && max > 0 -> Triple(context.getString(R.string.status_completed), R.drawable.bg_status_pill_completed, R.color.list_status_completed_text)
            current > 0 -> Triple(context.getString(R.string.status_in_progress), R.drawable.bg_status_pill_in_progress, R.color.list_status_in_progress_text)
            else -> Triple(context.getString(R.string.status_not_started), R.drawable.bg_status_pill_not_started, R.color.list_status_not_started_text)
        }
    }

    inner class GridViewHolder(val binding: ItemCourseGridBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    openCourse(getItem(position), 0)
                }
            }
            binding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, fromUser: Boolean) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < itemCount) {
                        val course = getItem(position)
                        val progress = progressMap?.get(course.courseId)
                        if (progress != null) {
                            val current = progress.current
                            if (fromUser && i <= current + 1) {
                                openCourse(course, seekBar.progress)
                            }
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        fun bind(course: Course) {
            val subject = CourseSubjectClassifier.classify(course.subjectLevel)
            bindCover(course, subject, binding.coverContainer, binding.ivCover, binding.ivSubjectIcon)
            binding.tvSubjectLabel.text = context.getString(subjectLabelRes(subject))
            binding.title.text = course.courseTitle
            binding.tvMeta.text = buildMetaLine(course)
            updateVisibilityForMyCourse(course, binding.isMyCourse, binding.checkbox)
            setupCheckbox(course, binding.checkbox) { bindingAdapterPosition }
            bindProgress(course)
        }

        fun bindPayloads(course: Course, hasProgressPayload: Boolean, hasSelectionPayload: Boolean, hasIdentityPayload: Boolean = false) {
            if (hasProgressPayload) bindProgress(course)
            if (hasSelectionPayload || hasIdentityPayload) {
                updateVisibilityForMyCourse(course, binding.isMyCourse, binding.checkbox)
                setupCheckbox(course, binding.checkbox) { bindingAdapterPosition }
            }
        }

        private fun bindProgress(course: Course) {
            val (current, max, hasProgress) = progressState(course)
            if (hasProgress && max > 0) {
                binding.courseProgress.visibility = View.VISIBLE
                binding.courseProgress.max = max
                binding.courseProgress.progress = current
                binding.tvProgressText.visibility = View.VISIBLE
                binding.tvProgressText.text = context.getString(R.string.steps_done_of_total, current, max)
                binding.tvPercent.visibility = View.VISIBLE
                val percent = if (max > 0) (current * 100 / max) else 0
                binding.tvPercent.text = "$percent%"
            } else {
                binding.courseProgress.visibility = View.GONE
                binding.tvProgressText.visibility = View.GONE
                binding.tvPercent.visibility = View.GONE
            }
        }
    }

    inner class ListViewHolder(val binding: ItemCourseListBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    openCourse(getItem(position), 0)
                }
            }
        }

        fun bind(course: Course) {
            val subject = CourseSubjectClassifier.classify(course.subjectLevel)
            bindCover(course, subject, binding.coverContainer, binding.ivCover, binding.ivSubjectIcon)
            binding.title.text = course.courseTitle
            binding.tvMeta.text = buildMetaLine(course)
            updateVisibilityForMyCourse(course, binding.isMyCourse, binding.checkbox)
            setupCheckbox(course, binding.checkbox) { bindingAdapterPosition }
            bindStatus(course)
        }

        fun bindPayloads(course: Course, hasProgressPayload: Boolean, hasSelectionPayload: Boolean, hasIdentityPayload: Boolean = false) {
            if (hasProgressPayload) bindStatus(course)
            if (hasSelectionPayload || hasIdentityPayload) {
                updateVisibilityForMyCourse(course, binding.isMyCourse, binding.checkbox)
                setupCheckbox(course, binding.checkbox) { bindingAdapterPosition }
            }
        }

        private fun bindStatus(course: Course) {
            val (current, max, hasProgress) = progressState(course)
            val (statusText, bgRes, textColorRes) = statusFor(current, max, hasProgress)
            binding.statusBadge.text = statusText
            binding.statusBadge.setBackgroundResource(bgRes)
            binding.statusBadge.setTextColor(ContextCompat.getColor(context, textColorRes))
        }
    }
}
