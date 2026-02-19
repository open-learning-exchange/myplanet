package org.ole.planet.myplanet.callback

import android.view.ViewGroup
import io.realm.RealmList
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser

interface OnNewsItemClickListener {
    fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean)
    fun addImage(llImage: ViewGroup?)
    fun onNewsItemClick(news: RealmNews?)
    fun clearImages()
    fun onDataChanged()
    fun onReplyPosted(newsId: String?) { onDataChanged() }
    fun onMemberSelected(userModel: RealmUser?)
    fun getCurrentImageList(): RealmList<String>?
}
