package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.realm.RealmObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.ItemMyLifeBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.helpwanted.HelpWantedFragment
import org.ole.planet.myplanet.ui.myPersonals.MyPersonalsFragment
import org.ole.planet.myplanet.ui.myhealth.MyHealthFragment
import org.ole.planet.myplanet.ui.mymeetup.MyMeetupDetailFragment
import org.ole.planet.myplanet.ui.news.NewsFragment
import org.ole.planet.myplanet.ui.references.ReferenceFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.Utilities

open class BaseDashboardFragmentPlugin : BaseContainerFragment() {
    fun handleClick(id: String?, title: String?, f: Fragment, v: TextView) {
        v.text = title
        v.setOnClickListener {
            if (homeItemClickListener != null) {
                val b = Bundle()
                b.putString("id", id)
                if (f is TeamDetailFragment) {
                    b.putBoolean("isMyTeam", true)
                }
                prefData.setTeamName(title)
                f.arguments = b
                homeItemClickListener?.openCallFragment(f)
            }
        }
    }

    private fun handleClickMyLife(title: String, v: View) {
        v.setOnClickListener {
            if (homeItemClickListener != null) {
                if (title == "mySubmissions") {
                    if (model?.id?.startsWith("guest") == false) {
                        homeItemClickListener?.openCallFragment(MySubmissionFragment())
                    } else {
                        guestDialog(requireContext())
                    }
                } else if (title == "Our News") {
                    homeItemClickListener?.openCallFragment(NewsFragment())
                } else if (title == "References") {
                    homeItemClickListener?.openCallFragment(ReferenceFragment())
                } else if (title == "Calendar") {
                    homeItemClickListener?.openCallFragment(CalendarFragment())
                } else if (title == "mySurvey") {
                    if (model?.id?.startsWith("guest") == false) {
                        homeItemClickListener?.openCallFragment(MySubmissionFragment.newInstance("survey"))
                    } else {
                        guestDialog(requireContext())
                    }
                } else if (title == "myAchievements") {
                    if (model?.id?.startsWith("guest") == false) {
                        homeItemClickListener?.openCallFragment(AchievementFragment())
                    } else {
                        guestDialog(requireContext())
                    }
                } else if (title == "myPersonals") {
                    if (model?.id?.startsWith("guest") == false) {
                        homeItemClickListener?.openCallFragment(MyPersonalsFragment())
                    } else {
                        guestDialog(requireContext())
                    }
                } else if (title == "Help Wanted") {
                    homeItemClickListener?.openCallFragment(HelpWantedFragment())
                } else if (title == "myHealth") {
                    if (model?.id?.startsWith("guest") == false) {
                        homeItemClickListener?.openCallFragment(MyHealthFragment())
                    } else {
                        guestDialog(requireContext())
                    }
                } else {
                    Utilities.toast(activity, getString(R.string.feature_not_available))
                }
            }
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
                textViewArray[itemCnt]?.let {
                    handleClick(obj.courseId, obj.courseTitle, TakeCourseFragment(), it)
                }
            }
            is RealmMeetup -> {
                textViewArray[itemCnt]?.let {
                    handleClick(obj.meetupId, obj.title, MyMeetupDetailFragment(), it)
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
        val user = UserProfileDbHandler(requireContext()).userModel
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
        myLifeList.add(RealmMyLife("ic_messages", userId, getString(R.string.messeges)))
        myLifeList.add(RealmMyLife("my_achievement", userId, getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId, getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, getString(R.string.my_survey)))
        myLifeList.add(RealmMyLife("ic_references", userId, getString(R.string.references)))
        myLifeList.add(RealmMyLife("ic_help_wanted", userId, getString(R.string.help_wanted)))
        myLifeList.add(RealmMyLife("ic_calendar", userId, getString(R.string.calendar)))
        myLifeList.add(RealmMyLife("ic_contacts", userId, getString(R.string.contacts)))
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