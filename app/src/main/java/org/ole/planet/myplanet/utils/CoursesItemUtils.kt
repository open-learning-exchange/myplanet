package org.ole.planet.myplanet.utils

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.signature.ObjectKey
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.MyCourse

internal object CoursesItemUtils {
    private val coverExistenceCache = FileExistenceCache()
    var timeProvider: TimeProvider = SystemTimeProvider()

    fun subjectColorRes(subject: CourseSubject): Int = when (subject) {
        CourseSubject.MATHEMATICS -> R.color.subject_math
        CourseSubject.LITERACY -> R.color.subject_literacy
        CourseSubject.HEALTH -> R.color.subject_health
        CourseSubject.SOCIAL_STUDIES -> R.color.subject_social_studies
        CourseSubject.TECHNOLOGY -> R.color.subject_technology
    }

    fun subjectIconRes(subject: CourseSubject): Int = when (subject) {
        CourseSubject.MATHEMATICS -> R.drawable.ic_subject_math
        CourseSubject.LITERACY -> R.drawable.ic_type_book
        CourseSubject.HEALTH -> R.drawable.ic_subject_health
        CourseSubject.SOCIAL_STUDIES -> R.drawable.ic_subject_social
        CourseSubject.TECHNOLOGY -> R.drawable.ic_subject_technology
    }

    fun subjectLabelRes(subject: CourseSubject): Int = when (subject) {
        CourseSubject.MATHEMATICS -> R.string.subject_label_mathematics
        CourseSubject.LITERACY -> R.string.subject_label_literacy
        CourseSubject.HEALTH -> R.string.subject_label_health
        CourseSubject.SOCIAL_STUDIES -> R.string.subject_label_social_studies
        CourseSubject.TECHNOLOGY -> R.string.subject_label_technology
    }

    private fun setCoverColor(context: Context, view: View, subject: CourseSubject) {
        val background = view.background?.mutate()
        if (background is GradientDrawable) {
            background.setColor(ContextCompat.getColor(context, subjectColorRes(subject)))
        }
    }

    fun bindCover(
        context: Context,
        viewMode: ListViewMode,
        course: Course,
        subject: CourseSubject,
        coverContainer: View,
        ivCover: ImageView,
        ivSubjectIcon: ImageView
    ) {
        setCoverColor(context, coverContainer, subject)
        val coverFile = MyCourse.getCoverImageFile(context, course.courseId, course.coverFileName)
        val model: Any? = if (coverExistenceCache.exists(coverFile, timeProvider.now())) {
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

        val fallbackHeight = if (viewMode == ListViewMode.GRID) {
            context.resources.getDimensionPixelSize(R.dimen.course_grid_cover_height)
        } else {
            context.resources.getDimensionPixelSize(R.dimen.course_list_cover_size)
        }

        val targetWidth = when {
            coverContainer.width > 0 -> coverContainer.width
            coverContainer.layoutParams?.width != null && coverContainer.layoutParams.width > 0 -> coverContainer.layoutParams.width
            else -> if (viewMode == ListViewMode.GRID) {
                val displayMetrics = context.resources.displayMetrics
                val widthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
                val cols = GridSpanCalculator.columnCount(widthDp)
                (displayMetrics.widthPixels / cols).coerceAtLeast(fallbackHeight)
            } else {
                fallbackHeight
            }
        }.coerceAtLeast(1)

        val targetHeight = when {
            coverContainer.height > 0 -> coverContainer.height
            coverContainer.layoutParams?.height != null && coverContainer.layoutParams.height > 0 -> coverContainer.layoutParams.height
            else -> fallbackHeight
        }.coerceAtLeast(1)

        Glide.with(context)
            .load(model)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(ObjectKey(course.courseRev.orEmpty()))
            .override(targetWidth, targetHeight)
            .centerCrop()
            .error(R.drawable.ole_logo)
            .into(ivCover)
    }

    fun buildMetaLine(context: Context, course: Course): String {
        val parts = mutableListOf<String>()
        parts.add(context.getString(R.string.course_steps_count, course.numberOfSteps))
        course.gradeLevel.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }
}
