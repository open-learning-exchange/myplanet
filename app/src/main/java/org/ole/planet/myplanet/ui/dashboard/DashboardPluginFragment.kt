package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.ItemCourseHomeBinding
import org.ole.planet.myplanet.databinding.ItemMyLifeBinding
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.health.MyHealthFragment
import org.ole.planet.myplanet.ui.personals.PersonalsFragment
import org.ole.planet.myplanet.ui.references.ReferencesFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsFragment
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.user.AchievementFragment
import org.ole.planet.myplanet.utils.DialogUtils.guestDialog
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

    private val imageResourceMap by lazy {
        mapOf(
            "ic_myhealth" to R.drawable.ic_myhealth,
            "my_achievement" to R.drawable.my_achievement,
            "ic_submissions" to R.drawable.ic_submissions,
            "ic_my_survey" to R.drawable.ic_my_survey,
            "ic_references" to R.drawable.ic_references,
            "ic_calendar" to R.drawable.ic_calendar,
            "ic_mypersonals" to R.drawable.ic_mypersonals
        )
    }

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

    fun createCourseChip(obj: DashboardItem?): View {
        val itemCourseHomeBinding = ItemCourseHomeBinding.inflate(LayoutInflater.from(activity))
        handleClick(obj?.id, obj?.title, TakeCourseFragment(), itemCourseHomeBinding.title)
        return itemCourseHomeBinding.root
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

    fun getMyLifeListBase(userId: String?): List<MyLife> = MyLife.defaultItems(userId, requireContext()::getString)
}
