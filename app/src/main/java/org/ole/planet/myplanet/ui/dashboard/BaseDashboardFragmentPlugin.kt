package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.realm.RealmObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.course.TakeCourseFragment
import org.ole.planet.myplanet.ui.helpwanted.HelpWantedFragment
import org.ole.planet.myplanet.ui.myPersonals.MyPersonalsFragment
import org.ole.planet.myplanet.ui.myhealth.MyHealthFragment
import org.ole.planet.myplanet.ui.mymeetup.MyMeetupDetailFragment
import org.ole.planet.myplanet.ui.news.NewsFragment
import org.ole.planet.myplanet.ui.references.ReferenceFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*

open class BaseDashboardFragmentPlugin : BaseContainerFragment() {
    fun handleClick(id: String?, title: String?, f: Fragment, v: TextView) {
        v.text = title
        v.setOnClickListener { view: View? ->
            if (homeItemClickListener != null) {
                val b = Bundle()
                b.putString("id", id)
                if (f is TeamDetailFragment) b.putBoolean("isMyTeam", true)
                f.arguments = b
                homeItemClickListener.openCallFragment(f)
            }
        }
    }

    fun handleClickMyLife(title: String, v: View) {
        v.setOnClickListener { view: View? ->
            if (homeItemClickListener != null) {
                if (title == getString(R.string.submission)) {
                    homeItemClickListener.openCallFragment(MySubmissionFragment())
                } else if (title == getString(R.string.news)) {
                    homeItemClickListener.openCallFragment(NewsFragment())
                } else if (title == getString(R.string.references)) {
                    homeItemClickListener.openCallFragment(ReferenceFragment())
                } else if (title == getString(R.string.calendar)) {
                    homeItemClickListener.openCallFragment(CalendarFragment())
                } else if (title == getString(R.string.my_survey)) {
                    homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("survey"))
                } else if (title == getString(R.string.achievements)) {
                    homeItemClickListener.openCallFragment(AchievementFragment())
                } else if (title == getString(R.string.mypersonals)) {
                    homeItemClickListener.openCallFragment(MyPersonalsFragment())
                } else if (title == getString(R.string.help_wanted)) {
                    homeItemClickListener.openCallFragment(HelpWantedFragment())
                } else if (title == getString(R.string.myhealth)) {
                    if (!model.id.startsWith("guest")) {
                        homeItemClickListener.openCallFragment(MyHealthFragment())
                    } else {
                        Utilities.toast(activity, "Feature not available for guest user")
                    }
                } else {
                    Utilities.toast(activity, "Feature Not Available")
                }
            }
        }
    }

    fun setTextViewProperties(textViewArray: Array<TextView?>, itemCnt: Int, obj: RealmObject?, c: Class<*>?) {
        textViewArray[itemCnt] = TextView(context)
        textViewArray[itemCnt]?.setPadding(20, 10, 20, 10)
        textViewArray[itemCnt]?.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textViewArray[itemCnt]?.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        if (obj is RealmMyLibrary) {
            textViewArray[itemCnt]?.text = obj.title
        } else if (obj is RealmMyCourse) {
            textViewArray[itemCnt]?.let { handleClick(obj.courseId, obj.courseTitle, TakeCourseFragment(), it) }
        } else if (obj is RealmMeetup) {
            textViewArray[itemCnt]?.let { handleClick(obj.meetupId, obj.title, MyMeetupDetailFragment(), it) }
        }
    }

    fun setTextColor(textView: TextView, itemCnt: Int, c: Class<*>?) {
        textView.setTextColor(resources.getColor(R.color.md_black_1000))
        setBackgroundColor(textView, itemCnt)
    }

    fun getLayout(itemCnt: Int, obj: RealmObject): View {
        val v = LayoutInflater.from(activity).inflate(R.layout.item_my_life, null)
        val img = v.findViewById<ImageView>(R.id.img)
        val counter = v.findViewById<TextView>(R.id.tv_count)
        val name = v.findViewById<TextView>(R.id.tv_name)
        setBackgroundColor(v, itemCnt)
        val title = (obj as RealmMyLife).title
        img.setImageResource(resources.getIdentifier(obj.imageId, "drawable", activity!!.packageName))
        name.text = title
        val user = UserProfileDbHandler(activity).userModel
        if (title == getString(R.string.my_survey)) {
            counter.visibility = View.VISIBLE
            val noOfSurvey = RealmSubmission.getNoOfSurveySubmissionByUser(user.id, mRealm)
            counter.text = noOfSurvey.toString() + ""
            Utilities.log("Count $noOfSurvey")
        } else {
            counter.visibility = View.GONE
        }
        handleClickMyLife(title, v)
        return v
    }

    fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId, getString(R.string.myhealth)))
        myLifeList.add(RealmMyLife("ic_messages", userId, getString(R.string.messeges)))
        myLifeList.add(RealmMyLife("my_achievement", userId, getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId, getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, getString(R.string.my_survey)))
        //        myLifeList.add(new RealmMyLife("ic_news", userId, getString(R.string.news)));
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
            v.setBackgroundColor(resources.getColor(R.color.md_grey_300))
        }
    }
}