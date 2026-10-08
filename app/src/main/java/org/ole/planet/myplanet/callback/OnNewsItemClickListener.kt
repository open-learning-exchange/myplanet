package org.ole.planet.myplanet.callback

import android.view.ViewGroup
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity

interface OnNewsItemClickListener {
    fun showReply(news: News?, fromLogin: Boolean, nonTeamMember: Boolean)
    fun addImage(llImage: ViewGroup?)
    fun onNewsItemClick(news: News?)
    fun clearImages()
    fun onDataChanged()
    fun onReplyPosted(newsId: String?) { onDataChanged() }
    fun onMemberSelected(userModel: UserEntity?)
    fun getCurrentImageList(): List<String>?
}
