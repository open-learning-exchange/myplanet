package org.ole.planet.myplanet.ui.dashboard

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.ItemCourseHomeBinding
import org.ole.planet.myplanet.databinding.ItemMyLifeBinding
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.health.MyHealthFragment
import org.ole.planet.myplanet.ui.life.LifeItemDefaults
import org.ole.planet.myplanet.ui.personals.PersonalsFragment
import org.ole.planet.myplanet.ui.references.ReferencesFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsFragment
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.user.AchievementFragment
import org.ole.planet.myplanet.utils.DialogUtils.guestDialog
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

enum class MyLifeRoute {
    SUBMISSIONS,
    REFERENCES,
    CALENDAR,
    SURVEYS,
    ACHIEVEMENTS,
    PERSONALS,
    HEALTH,
    UNKNOWN
}

internal fun myLifeRouteFor(imageId: String?): MyLifeRoute {
    return when (imageId) {
        "ic_submissions" -> MyLifeRoute.SUBMISSIONS
        "ic_references" -> MyLifeRoute.REFERENCES
        "ic_calendar" -> MyLifeRoute.CALENDAR
        "ic_my_survey" -> MyLifeRoute.SURVEYS
        "my_achievement" -> MyLifeRoute.ACHIEVEMENTS
        "ic_mypersonals" -> MyLifeRoute.PERSONALS
        "ic_myhealth" -> MyLifeRoute.HEALTH
        else -> MyLifeRoute.UNKNOWN
    }
}

open class DashboardPluginFragment : BaseContainerFragment() {

    private val dashboardViewModel: DashboardViewModel by activityViewModels()

    private val imageResourceMap get() = LifeItemDefaults.knownDrawables

    open fun handleClick(id: String?, title: String?, f: Fragment, v: TextView) {
        v.text = title
        v.setOnClickListener {
            if (homeItemClickListener != null) {
                if (f is TeamDetailFragment) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val teamType = id?.let { dashboardViewModel.getTeamType(it) }
                        val optimizedFragment = TeamDetailFragment.newInstance(
                            teamId = id ?: "",
                            teamName = title ?: "",
                            teamType = teamType ?: "",
                            isMyTeam = true
                        )
                        prefData.setTeamName(title)
                        homeItemClickListener?.openCallFragment(optimizedFragment)
                    }
                } else {
                    val b = Bundle()
                    b.putString("id", id)
                    f.arguments = b
                    prefData.setTeamName(title)
                    homeItemClickListener?.openCallFragment(f)
                }
            }
        }
    }

    private fun handleClickMyLife(imageId: String?, v: View) {
        v.setOnClickListener {
            homeItemClickListener?.let { listener ->
                when (myLifeRouteFor(imageId)) {
                    MyLifeRoute.SUBMISSIONS -> openIfLoggedIn { listener.openCallFragment(SubmissionsFragment()) }
                    MyLifeRoute.REFERENCES -> listener.openCallFragment(ReferencesFragment())
                    MyLifeRoute.CALENDAR -> listener.openCallFragment(CalendarFragment())
                    MyLifeRoute.SURVEYS -> openIfLoggedIn { listener.openCallFragment(SubmissionsFragment.newInstance("survey")) }
                    MyLifeRoute.ACHIEVEMENTS -> openIfLoggedIn { listener.openCallFragment(AchievementFragment()) }
                    MyLifeRoute.PERSONALS -> openIfLoggedIn { listener.openCallFragment(PersonalsFragment()) }
                    MyLifeRoute.HEALTH -> openIfLoggedIn { listener.openCallFragment(MyHealthFragment()) }
                    MyLifeRoute.UNKNOWN -> Utilities.toast(activity, getString(R.string.feature_not_available))
                }
            }
        }
    }

    private inline fun openIfLoggedIn(action: () -> Unit) {
        if (model?.id?.startsWith("guest") == false) {
            action()
        } else {
            guestDialog(requireContext())
        }
    }

    fun createCourseChip(course: MyCourse): View {
        val itemCourseHomeBinding = ItemCourseHomeBinding.inflate(LayoutInflater.from(activity))
        handleClick(course.courseId, course.courseTitle, TakeCourseFragment(), itemCourseHomeBinding.title)
        loadCourseCover(itemCourseHomeBinding, course)
        return itemCourseHomeBinding.root
    }

    private fun loadCourseCover(binding: ItemCourseHomeBinding, course: MyCourse) {
        val context = binding.root.context
        binding.root.clipToOutline = true
        val coverFile = MyCourse.getCoverImageFile(context, course.courseId, course.coverFileName)
        val model: Any = if (coverFile?.exists() == true) {
            coverFile
        } else {
            UrlUtils.getCourseImageUrl(course.courseId, course.coverFileName)?.let { url ->
                GlideUrl(url, LazyHeaders.Builder().addHeader("Authorization", UrlUtils.header).build())
            } ?: return
        }
        Glide.with(binding.cover)
            .load(model)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(ObjectKey(course.courseRev ?: ""))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean = true

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.cover.visibility = View.VISIBLE
                    binding.content.visibility = View.INVISIBLE
                    binding.coverTitle.text = binding.title.text
                    binding.coverTitle.visibility = View.VISIBLE
                    binding.root.setOnClickListener { binding.title.performClick() }
                    return false
                }
            })
            .into(binding.cover)
    }

    fun getLayout(obj: DashboardItem, surveyCount: Int? = null): View {
        val itemMyLifeBinding = ItemMyLifeBinding.inflate(LayoutInflater.from(activity))
        val v = itemMyLifeBinding.root

        val title = obj.title
        val imageResId = imageResourceMap[obj.imageId] ?: R.drawable.ic_myhealth
        itemMyLifeBinding.img.setImageResource(imageResId)
        itemMyLifeBinding.tvName.text = title

        if (title == getString(R.string.my_survey)) {
            itemMyLifeBinding.tvCount.visibility = View.VISIBLE
            itemMyLifeBinding.tvCount.text = surveyCount?.toString() ?: "0"
        } else {
            itemMyLifeBinding.tvCount.visibility = View.GONE
        }

        handleClickMyLife(obj.imageId, v)
        return v
    }

    fun getMyLifeListBase(userId: String?): List<MyLife> = LifeItemDefaults.forUser(userId, requireContext()::getString)
}
