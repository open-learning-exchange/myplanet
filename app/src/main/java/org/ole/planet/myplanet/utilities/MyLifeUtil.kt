package org.ole.planet.myplanet.utilities

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLife

object MyLifeUtil {
    fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(createMyLife("ic_myhealth", "My Health", userId))
        myLifeList.add(createMyLife("ic_my_survey", "My Surveys", userId))
        myLifeList.add(createMyLife("my_achievement", "My Achievements", userId))
        myLifeList.add(createMyLife("ic_calendar", "Calendar", userId))
        myLifeList.add(createMyLife("ic_contacts", "Contact", userId))
        myLifeList.add(createMyLife("ic_feedback", "Feedback", userId))
        myLifeList.add(createMyLife("ic_help", "Help", userId))
        myLifeList.add(createMyLife("ic_logout", "Logout", userId))
        myLifeList.add(createMyLife("ic_newspaper", "News", userId))
        myLifeList.add(createMyLife("ic_notes", "Notes", "userId"))
        myLifeList.add(createMyLife("my_submission", "My Submissions", userId))
        myLifeList.add(createMyLife("ic_stories", "Stories", userId))
        myLifeList.add(createMyLife("ic_sync", "Sync", userId))
        myLifeList.add(createMyLife("ic_wifi", "Wifi", userId))
        return myLifeList
    }

    private fun createMyLife(imageId: String, title: String, userId: String?): RealmMyLife {
        val life = RealmMyLife()
        life.imageId = imageId
        life.title = title
        life.userId = userId
        return life
    }
}
