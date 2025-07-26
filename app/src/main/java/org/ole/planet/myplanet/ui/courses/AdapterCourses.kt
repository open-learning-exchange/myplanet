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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import io.realm.Realm
import java.util.Collections
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
    private var courseList: List<RealmMyCourse?>,
    private val map: HashMap<String?, JsonObject>,
    private val userProfileDbHandler: UserProfileDbHandler
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
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
    }

    fun setmRealm(mRealm: Realm?) {
        this.mRealm = mRealm
    }

    fun setRatingChangeListener(ratingChangeListener: OnRatingChangeListener?) {
        this.ratingChangeListener = ratingChangeListener
    }

    fun getCourseList(): List<RealmMyCourse?> {
        return courseList
    }

    fun setOriginalCourseList(courseList: List<RealmMyCourse?>){
        this.courseList = courseList
        notifyDataSetChanged()
    }

    fun setCourseList(courseList: List<RealmMyCourse?>) {
        this.courseList = courseList
        notifyDataSetChanged()
    }

    private fun sortCourseListByTitle() {
        Collections.sort(courseList) { course1: RealmMyCourse?, course2: RealmMyCourse? ->
            if (isTitleAscending) {
                return@sort course1!!.courseTitle!!.compareTo(course2!!.courseTitle!!, ignoreCase = true)
            } else {
                return@sort course2!!.courseTitle!!.compareTo(course1!!.courseTitle!!, ignoreCase = true)
            }
        }
    }

    private fun sortCourseList() {
        Collections.sort(courseList) { course1, course2 ->
            if (isAscending) {
                course1?.createdDate!!.compareTo(course2?.createdDate!!)
            } else {
                course2?.createdDate!!.compareTo(course1?.createdDate!!)
            }
        }
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        sortCourseListByTitle()
        notifyDataSetChanged()
    }

    fun toggleSortOrder() {
        isAscending = !isAscending
        sortCourseList()
        notifyDataSetChanged()
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
        val course = courseList[position] ?: return

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
                openCourse(courseList[position], 0)
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
            if (course.isMyCourse) {
                holder.rowCourseBinding.checkbox.visibility = View.GONE
            } else {
                holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
                holder.rowCourseBinding.checkbox.isChecked = selectedItems.contains(course)
                holder.rowCourseBinding.checkbox.setOnClickListener { view: View ->
                    holder.rowCourseBinding.checkbox.contentDescription =
                        context.getString(R.string.select_res_course, course.courseTitle)
                    Utilities.handleCheck((view as CheckBox).isChecked, position, selectedItems, courseList)
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
        val selectableCourses = courseList.filterNotNull().filter { !it.isMyCourse }
        areAllSelected = selectedItems.size == selectableCourses.size && selectableCourses.isNotEmpty()
        return areAllSelected
    }

    fun selectAllItems(selectAll: Boolean) {
        selectedItems.clear()
        
        if (selectAll) {
            val selectableCourses = courseList.filterNotNull().filter { !it.isMyCourse }
            selectedItems.addAll(selectableCourses)
        }
        
        val updatedPositions = mutableListOf<Int>()
        courseList.forEachIndexed { index, course ->
            if (course != null && !course.isMyCourse) {
                updatedPositions.add(index)
            }
        }
        
        updatedPositions.forEach { position ->
            notifyItemChanged(position)
        }
        
        listener?.onSelectedListChange(selectedItems)
    }

    private fun displayTagCloud(flexboxDrawable: FlexboxLayout, position: Int) {
        flexboxDrawable.removeAllViews()
        val chipCloud = ChipCloud(context, flexboxDrawable, config)
        val tags: List<RealmTag>? = mRealm?.where(RealmTag::class.java)?.equalTo("db", "courses")?.equalTo("linkId", courseList[position]!!.id)?.findAll()
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
        if (map.containsKey(courseList[position]!!.courseId)) {
            val `object` = map[courseList[position]!!.courseId]
            CourseRatingUtils.showRating(
                `object`,
                viewHolder.rowCourseBinding.rating,
                viewHolder.rowCourseBinding.timesRated,
                viewHolder.rowCourseBinding.ratingBar
            )
        } else {
            viewHolder.rowCourseBinding.ratingBar.rating = 0f
        }
    }

    private fun showProgress(binding: RowCourseBinding, position: Int) {
        if (progressMap?.containsKey(courseList[position]?.courseId) == true) {
            val ob = progressMap!![courseList[position]?.courseId]
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

    override fun getItemCount(): Int {
        return courseList.size
    }

    fun updateCourseList(newCourseList: List<RealmMyCourse?>) {
        this.courseList = newCourseList
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun setRatingMap(newRatingMap: HashMap<String?, JsonObject>) {
        this.map.clear()
        this.map.putAll(newRatingMap)
        notifyDataSetChanged()
    }

    internal inner class ViewHoldercourse(val rowCourseBinding: RowCourseBinding) :
        RecyclerView.ViewHolder(rowCourseBinding.root) {
        private var adapterPosition = 0

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    openCourse(courseList[adapterPosition], 0)
                }
            }
            rowCourseBinding.courseProgress.scaleY = 0.3f
            rowCourseBinding.courseProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < courseList.size) {
                        if (progressMap?.containsKey(courseList[bindingAdapterPosition]?.courseId) == true) {
                            val ob = progressMap!![courseList[bindingAdapterPosition]?.courseId]
                            val current = getInt("current", ob)
                            if (b && i <= current + 1) {
                                openCourse(courseList[bindingAdapterPosition], seekBar.progress)
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
