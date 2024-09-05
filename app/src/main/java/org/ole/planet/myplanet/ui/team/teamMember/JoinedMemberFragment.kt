package org.ole.planet.myplanet.ui.team.teamMember

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMember
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

class JoinedMemberFragment : BaseMemberFragment() {
    override val list: List<RealmUserModel>
        @RequiresApi(Build.VERSION_CODES.O)
        get() = getJoinedMember(teamId, mRealm)
    override val adapter: RecyclerView.Adapter<*>
        @RequiresApi(Build.VERSION_CODES.O)
        get() = AdapterJoinedMember(requireActivity(), list, mRealm, teamId)

    override val layoutManager: RecyclerView.LayoutManager
        get() {
            val columns = when (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
                Configuration.SCREENLAYOUT_SIZE_LARGE -> 3
                Configuration.SCREENLAYOUT_SIZE_NORMAL -> 2
                Configuration.SCREENLAYOUT_SIZE_SMALL -> 1
                else -> 1
            }
            return GridLayoutManager(activity, columns)
        }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}
