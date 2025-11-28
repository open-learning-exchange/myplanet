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
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnCourseItemSelected
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.RowCourseBinding
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.CourseRatingUtils
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getInt
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.SelectionUtils
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities

class AdapterCourses(
    private val context: Context,
    private val map: HashMap<String?, JsonObject>,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val tagRepository: TagRepository,
    private val lifecycleOwner: LifecycleOwner
) : ListAdapter<CourseItemDto, RecyclerView.ViewHolder>(courseItemCallback) {
    private val selectedItems: MutableList<CourseItemDto> = ArrayList()
    private var listener: OnCourseItemSelected? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var progressMap: HashMap<String?, JsonObject>? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private val config: ChipCloudConfig
    private var areAllSelected = false
    var userModel: RealmUserModel?= null
    private val tagCache: MutableMap<String, List<RealmTag>> = mutableMapOf()
    private val tagRequestsInProgress: MutableSet<String> = mutableSetOf()

    companion object {
        private const val TAG_PAYLOAD = "payload_tags"
        private const val RATING_PAYLOAD = "payload_rating"
        private const val PROGRESS_PAYLOAD = "payload_progress"

        val courseItemCallback = DiffUtils.itemCallback<CourseItemDto>(
            areItemsTheSame = { old, new -> old.id == new.id },
            areContentsTheSame = { old, new -> old == new },
            getChangePayload = { old, new ->
                val bundle = Bundle()
                if (old.rating != new.rating) {
                    bundle.putBoolean(RATING_PAYLOAD, true)
                }
                if (old.progress != new.progress) {
                    bundle.putBoolean(PROGRESS_PAYLOAD, true)
                }
                if (bundle.isEmpty) null else bundle
            }
        )
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
        val course = getItem(position) ?: return

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

        userModel = userProfileDbHandler.userModel
        val isGuest = userModel?.isGuest() ?: true
        if (!isGuest) setupRatingBar(holder, course)
        setupCheckbox(holder, course, position, isGuest)

        updateRatingViews(holder, position)
        updateProgressViews(holder, position)

        holder.rowCourseBinding.root.setOnClickListener {
            val newPosition = holder.bindingAdapterPosition
            if (newPosition != RecyclerView.NO_POSITION) {
                openCourse(getItem(newPosition), 0)
            }
        }
    }

    private fun updateVisibilityForMyCourse(holder: ViewHoldercourse, course: CourseItemDto) {
        if (course.isMyCourse) {
            holder.rowCourseBinding.isMyCourse.visibility = View.VISIBLE
            holder.rowCourseBinding.checkbox.visibility = View.GONE
        } else {
            holder.rowCourseBinding.isMyCourse.visibility = View.GONE
            holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
        }
    }

    private fun configureDescription(holder: ViewHoldercourse, course: CourseItemDto, position: Int) {
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

    private fun configureDateViews(holder: ViewHoldercourse, course: CourseItemDto) {
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

    private fun setupRatingBar(holder: ViewHoldercourse, course: CourseItemDto) {
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

    private fun setupCheckbox(holder: ViewHoldercourse, course: CourseItemDto, position: Int, isGuest: Boolean) {
        if (!isGuest) {
            if (course.isMyCourse) {
                holder.rowCourseBinding.checkbox.visibility = View.GONE
            } else {
                holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
                holder.rowCourseBinding.checkbox.isChecked = selectedItems.contains(course)
                holder.rowCourseBinding.checkbox.setOnClickListener { view: View ->
                    holder.rowCourseBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, course.courseTitle)
                    SelectionUtils.handleCheck((view as CheckBox).isChecked, position, selectedItems, currentList)
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
        val selectableCourses = currentList.filterNotNull().filter { !it.isMyCourse }
        areAllSelected = selectedItems.size == selectableCourses.size && selectableCourses.isNotEmpty()
        return areAllSelected
    }

    fun selectAllItems(selectAll: Boolean) {
        selectedItems.clear()

        if (selectAll) {
            val selectableCourses = currentList.filterNotNull().filter { !it.isMyCourse }
            selectedItems.addAll(selectableCourses)
        }

        val updatedPositions = mutableListOf<Int>()
        currentList.forEachIndexed { index, course ->
            if (course != null && !course.isMyCourse) {
                updatedPositions.add(index)
            }
        }

        updatedPositions.forEach { position ->
            notifyItemChanged(position)
        }

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
                updateRatingViews(holder, position)
            }
            if (hasProgressPayload) {
                updateProgressViews(holder, position)
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
                    notifyItemChanged(adapterPosition, TAG_PAYLOAD)
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

    private fun updateRatingViews(holder: ViewHoldercourse, position: Int) {
        val course = getItem(position) ?: return
        if (map.containsKey(course.courseId)) {
            val ratingObject = map[course.courseId]
            CourseRatingUtils.showRating(
                context,
                ratingObject,
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

    private fun updateProgressViews(holder: ViewHoldercourse, position: Int) {
        val course = getItem(position) ?: return
        val progress = progressMap?.get(course.courseId)
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

    private fun openCourse(course: CourseItemDto?, step: Int) {
        if (homeItemClickListener != null) {
            val f: Fragment = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", course?.courseId)
            b.putInt("position", step)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    internal inner class ViewHoldercourse(val rowCourseBinding: RowCourseBinding) :
        RecyclerView.ViewHolder(rowCourseBinding.root) {
        private var adapterPosition = 0

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    openCourse(getItem(adapterPosition), 0)
                }
            }
            rowCourseBinding.courseProgress.scaleY = 0.3f
            rowCourseBinding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < currentList.size) {
                        if (progressMap?.containsKey(getItem(bindingAdapterPosition)?.courseId) == true) {
                            val ob = progressMap!![getItem(bindingAdapterPosition)?.courseId]
                            val current = getInt("current", ob)
                            if (b && i <= current + 1) {
                                openCourse(getItem(bindingAdapterPosition), seekBar.progress)
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
