package org.ole.planet.myplanet.callback

import android.view.ViewGroup
import io.realm.RealmList
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

interface OnNewsItemClickListener {
    fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean)
    fun addImage(llImage: ViewGroup?)
    fun onNewsItemClick(news: RealmNews?)
    fun clearImages()
    fun onDataChanged()
    fun onMemberSelected(userModel: RealmUserModel?)
    fun getCurrentImageList(): RealmList<String>?
}
