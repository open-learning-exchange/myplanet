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
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
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
import org.ole.planet.myplanet.utilities.JsonUtils.getInt
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern

class AdapterCourses(private val context: Context, private var courseList: List<RealmMyCourse?>, private val map: HashMap<String?, JsonObject>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowCourseBinding: RowCourseBinding
    private val selectedItems: MutableList<RealmMyCourse?> = ArrayList()
    private var listener: OnCourseItemSelected? = null
    private var homeItemClickListener: OnHomeItemClickListener? = null
    private var progressMap: HashMap<String?, JsonObject>? = null
    private var ratingChangeListener: OnRatingChangeListener? = null
    private var mRealm: Realm? = null
    private val config: ChipCloudConfig
    private var isAscending = true
    private var isTitleAscending = false
    private var areAllSelected = true
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

    fun setCourseList(courseList: List<RealmMyCourse?>) {
        this.courseList = courseList
        sortCourseList()
        sortCourseListByTitle()
        notifyDataSetChanged()
    }

    private fun sortCourseListByTitle() {
        Collections.sort(courseList) { course1: RealmMyCourse?, course2: RealmMyCourse? ->
            if (isTitleAscending) {
                return@sort course1!!.courseTitle.compareTo(course2!!.courseTitle, ignoreCase = true)
            } else {
                return@sort course2!!.courseTitle.compareTo(course1!!.courseTitle, ignoreCase = true)
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
        rowCourseBinding = RowCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHoldercourse(rowCourseBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHoldercourse) {
            holder.bind(position)
            val course = courseList[position]
            if (course != null) {
                if (course.isMyCourse) {
                    holder.rowCourseBinding.isMyCourse.visibility = View.VISIBLE
                    holder.rowCourseBinding.checkbox.visibility = View.GONE
                } else {
                    holder.rowCourseBinding.isMyCourse.visibility = View.GONE
                    holder.rowCourseBinding.checkbox.visibility = View.VISIBLE
                }

                holder.rowCourseBinding.title.text = course.courseTitle
                holder.rowCourseBinding.description.apply {
                    text = course.description
                    val markdownContentWithLocalPaths = prependBaseUrlToImages(
                        course.description, "file://${MainApplication.context.getExternalFilesDir(null)}/ole/"
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

                if (course.gradeLevel.isEmpty() && course.subjectLevel.isEmpty()) {
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
                setTextViewContent(holder.rowCourseBinding.gradLevel, course.gradeLevel, holder.rowCourseBinding.gradLevel, context.getString(R.string.grade_level_colon))
                setTextViewContent(holder.rowCourseBinding.subjectLevel, course.subjectLevel, holder.rowCourseBinding.subjectLevel, context.getString(R.string.subject_level_colon))
                holder.rowCourseBinding.courseProgress.max = course.getNumberOfSteps()
                displayTagCloud(holder.rowCourseBinding.flexboxDrawable, position)
                userModel = UserProfileDbHandler(context).userModel
                if (!userModel?.isGuest()!!) {
                    holder.rowCourseBinding.ratingBar.setOnTouchListener { _: View?, event: MotionEvent ->
                        if (event.action == MotionEvent.ACTION_UP) homeItemClickListener?.showRatingDialog(
                            "course", course.courseId, course.courseTitle, ratingChangeListener
                        )
                        true
                    }
                }
                if (!userModel?.isGuest()!!) {
                    holder.rowCourseBinding.checkbox.isChecked = selectedItems.contains(course)
                    holder.rowCourseBinding.checkbox.setOnClickListener { view: View ->
                        holder.rowCourseBinding.checkbox.contentDescription = context.getString(R.string.select_res_course, course.courseTitle)
                        Utilities.handleCheck((view as CheckBox).isChecked, position, selectedItems, courseList)
                        listener?.onSelectedListChange(selectedItems)
                    }
                } else {
                    holder.rowCourseBinding.checkbox.visibility = View.GONE
                }
                showProgressAndRating(position, holder)

                holder.rowCourseBinding.root.setOnClickListener {
                    if (position != RecyclerView.NO_POSITION) {
                        openCourse(courseList[position], 0)
                    }
                }
            }
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
        areAllSelected = selectedItems.size == courseList.size
        return areAllSelected
    }

    fun selectAllItems(selectAll: Boolean) {
        selectedItems.clear()
        if (selectAll) {
            selectedItems.addAll(courseList.filter { course ->
                course != null && !course.isMyCourse
            })
        }
        notifyDataSetChanged()
        listener?.onSelectedListChange(selectedItems)
    }

    private fun displayTagCloud(flexboxDrawable: FlexboxLayout, position: Int) {
        flexboxDrawable.removeAllViews()
        val chipCloud = ChipCloud(context, flexboxDrawable, config)

        mRealm?.let { realm ->
            val tags = realm.query<RealmTag>(RealmTag::class, "db == $0 AND linkId == $1", "courses",
                courseList[position]?.id ?: "").find()
            showTags(tags, chipCloud)
        }
    }

    private fun showTags(tags: RealmResults<RealmTag>, chipCloud: ChipCloud) {
        tags.forEach { tag ->
            mRealm?.query<RealmTag>(RealmTag::class, "id == $0", tag.tagId)
                ?.first()?.find()?.let { parent -> showChip(chipCloud, parent) }
        }
    }

    private fun showChip(chipCloud: ChipCloud, parent: RealmTag?) {
        chipCloud.addChip(if (parent != null) parent.name else "")
        chipCloud.setListener { _: Int, _: Boolean, b1: Boolean ->
            if (b1 && listener != null) {
                listener?.onTagClicked(parent)
            }
        }
    }

    private fun showProgressAndRating(position: Int, holder: RecyclerView.ViewHolder) {
        val viewHolder = holder as ViewHoldercourse
        showProgress(position)
        if (map.containsKey(courseList[position]?.courseId)) {
            val `object` = map[courseList[position]?.courseId]
            showRating(`object`, viewHolder.rowCourseBinding.rating, viewHolder.rowCourseBinding.timesRated, viewHolder.rowCourseBinding.ratingBar)
        } else {
            viewHolder.rowCourseBinding.ratingBar.rating = 0f
        }
    }

    private fun showProgress(position: Int) {
        if (progressMap?.containsKey(courseList[position]?.courseId) == true) {
            val ob = progressMap!![courseList[position]?.courseId]
            rowCourseBinding.courseProgress.max = getInt("max", ob)
            rowCourseBinding.courseProgress.progress = getInt("current", ob)
            if (getInt("current", ob) < getInt("max", ob))
                rowCourseBinding.courseProgress.secondaryProgress = getInt("current", ob) + 1
            rowCourseBinding.courseProgress.visibility = View.VISIBLE
        } else {
            rowCourseBinding.courseProgress.visibility = View.GONE
        }
    }

    private fun openCourse(realmMyCourses: RealmMyCourse?, i: Int) {
        if (homeItemClickListener != null) {
            val f: Fragment = TakeCourseFragment()
            val b = Bundle()
            b.putString("id", realmMyCourses?.courseId)
            b.putInt("position", i)
            f.arguments = b
            homeItemClickListener?.openCallFragment(f)
        }
    }

    override fun getItemCount(): Int {
        return courseList.size
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

    companion object {
        @JvmStatic
        fun showRating(`object`: JsonObject?, average: TextView?, ratingCount: TextView?, ratingBar: AppCompatRatingBar?) {
            if (average != null) {
                average.text = String.format(Locale.getDefault(), "%.2f", `object`?.get("averageRating")?.asFloat)
            }
            if (ratingCount != null) {
                ratingCount.text = context.getString(R.string.rating_count_format, `object`?.get("total")?.asInt)
            }
            if (`object` != null) {
                if (`object`.has("ratingByUser"))
                    if (ratingBar != null) {
                        ratingBar.rating = `object`["ratingByUser"].asInt.toFloat()
                    }
            }
        }

        fun prependBaseUrlToImages(markdownContent: String?, baseUrl: String): String {
            val pattern = "!\\[.*?]\\((.*?)\\)"
            val imagePattern = Pattern.compile(pattern)
            val matcher = markdownContent?.let { imagePattern.matcher(it) }
            val result = StringBuffer()
            if (matcher != null) {
                while (matcher.find()) {
                    val relativePath = matcher.group(1)
                    val modifiedPath = relativePath?.replaceFirst("resources/".toRegex(), "")
                    val fullUrl = baseUrl + modifiedPath
                    matcher.appendReplacement(result, "<img src=$fullUrl width=150 height=100/>")
                }
            }
            matcher?.appendTail(result)
            return result.toString()
        }
    }
}
