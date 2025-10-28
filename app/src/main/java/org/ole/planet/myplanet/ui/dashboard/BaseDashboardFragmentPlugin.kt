package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.realm.RealmObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.ItemMyLifeBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.ui.mymeetup.MyMeetupDetailFragment
import org.ole.planet.myplanet.ui.navigation.DashboardDestination
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.Utilities

open class BaseDashboardFragmentPlugin : BaseContainerFragment() {
    fun handleClick(title: String?, v: TextView, destinationProvider: () -> DashboardDestination) {
        v.text = title
        v.setOnClickListener {
            homeItemClickListener?.let { listener ->
                prefData.setTeamName(title)
                listener.openCallFragment(destinationProvider())
            }
        }
    }

    private fun handleClickMyLife(title: String, v: View) {
        v.setOnClickListener {
            homeItemClickListener?.let { listener ->
                when (title) {
                    "mySubmissions" -> openIfLoggedIn { listener.openCallFragment(DashboardDestination.MySubmission()) }
                    "References" -> listener.openCallFragment(DashboardDestination.References)
                    "Calendar" -> listener.openCallFragment(DashboardDestination.Calendar)
                    "mySurveys" -> openIfLoggedIn { listener.openCallFragment(DashboardDestination.MySubmission("survey")) }
                    "myAchievements" -> openIfLoggedIn { listener.openCallFragment(DashboardDestination.Achievements) }
                    "myPersonals" -> openIfLoggedIn { listener.openCallFragment(DashboardDestination.MyPersonals) }
                    "myHealth" -> openIfLoggedIn { listener.openCallFragment(DashboardDestination.MyHealth) }
                    else -> Utilities.toast(activity, getString(R.string.feature_not_available))
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

    fun setTextViewProperties(textViewArray: Array<TextView?>, itemCnt: Int, obj: RealmObject?) {
        textViewArray[itemCnt] = TextView(context)
        textViewArray[itemCnt]?.setPadding(20, 10, 20, 10)
        textViewArray[itemCnt]?.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textViewArray[itemCnt]?.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        when (obj) {
            is RealmMyLibrary -> {
                textViewArray[itemCnt]?.text = obj.title
            }
            is RealmMyCourse -> {
                textViewArray[itemCnt]?.let { view ->
                    handleClick(obj.courseTitle, view) {
                        DashboardDestination.TakeCourse(obj.courseId)
                    }
                }
            }
            is RealmMeetup -> {
                textViewArray[itemCnt]?.let { view ->
                    handleClick(obj.title, view) {
                        DashboardDestination.Custom(
                            fragmentFactory = {
                                MyMeetupDetailFragment().apply {
                                    arguments = Bundle().apply { putString("id", obj.meetupId) }
                                }
                            },
                            stableTag = "MyMeetup_${obj.meetupId}"
                        )
                    }
                }
            }
        }
    }

    fun setTextColor(textView: TextView, itemCnt: Int) {
        textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
        setBackgroundColor(textView, itemCnt)
    }

    fun getLayout(itemCnt: Int, obj: RealmObject): View {
        val itemMyLifeBinding = ItemMyLifeBinding.inflate(LayoutInflater.from(activity))
        val v = itemMyLifeBinding.root
        setBackgroundColor(v, itemCnt)

        val title = (obj as RealmMyLife).title
        val user = profileDbHandler.userModel
        itemMyLifeBinding.img.setImageResource(resources.getIdentifier(obj.imageId, "drawable", requireActivity().packageName))
        itemMyLifeBinding.tvName.text = title

        if (title == getString(R.string.my_survey)) {
            itemMyLifeBinding.tvCount.visibility = View.VISIBLE
            val noOfSurvey = RealmSubmission.getNoOfSurveySubmissionByUser(user?.id, mRealm)
            itemMyLifeBinding.tvCount.text = noOfSurvey.toString()
        } else {
            itemMyLifeBinding.tvCount.visibility = View.GONE
        }

        if (title != null) {
            handleClickMyLife(title, v)
        }
        return v
    }

    fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId, getString(R.string.myhealth)))
        myLifeList.add(RealmMyLife("my_achievement", userId, getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId, getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, getString(R.string.my_survey)))
        myLifeList.add(RealmMyLife("ic_references", userId, getString(R.string.references)))
        myLifeList.add(RealmMyLife("ic_calendar", userId, getString(R.string.calendar)))
        myLifeList.add(RealmMyLife("ic_mypersonals", userId, getString(R.string.mypersonals)))
        return myLifeList
    }

    fun setBackgroundColor(v: View, count: Int) {
        if (count % 2 == 0) {
            v.setBackgroundResource(R.drawable.light_rect)
        } else {
            v.setBackgroundResource(R.color.dashboard_item_alternative)
        }
    }
}
