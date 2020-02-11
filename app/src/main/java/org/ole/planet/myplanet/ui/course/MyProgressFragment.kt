package org.ole.planet.myplanet.ui.course


import android.os.Bundle
import android.service.autofill.UserData
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_my_progress.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import org.w3c.dom.UserDataHandler

/**
 * A simple [Fragment] subclass.
 */
class MyProgressFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_my_progress, container, false)
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var realm = DatabaseService(activity!!).realmInstance
        var user = UserProfileDbHandler(activity!!).userModel
        var mycourses = RealmMyCourse.getMyCourseByUserId(user.getId(), realm.where(RealmMyCourse::class.java).findAll())
        mycourses.forEach {

        }

        var submissions = realm.where(RealmSubmission::class.java).equalTo("userId", user.id).equalTo("type","exam").findAll()
        var examsMap = RealmSubmission.getExamMap(realm, submissions)
        Utilities.log("${submissions.size}");
        rv_myprogress.layoutManager = LinearLayoutManager(activity!!)
        rv_myprogress.adapter = AdapterMyProgress(activity!!,realm, submissions, examsMap)
    }

}
