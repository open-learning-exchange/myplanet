package org.ole.planet.myplanet.ui.userprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentUserDetailBinding
import org.ole.planet.myplanet.databinding.ItemTitleDescBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.TimeUtils.getFormatedDate
import org.ole.planet.myplanet.utilities.Utilities

class UserDetailFragment : Fragment() {
    private lateinit var fragmentUserDetailBinding: FragmentUserDetailBinding
    lateinit var itemTitleDescBinding: ItemTitleDescBinding
    private var userId: String? = null
    private var user: RealmUserModel? = null
    private lateinit var db: UserProfileDbHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString("id")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentUserDetailBinding = FragmentUserDetailBinding.inflate(inflater, container, false)
        fragmentUserDetailBinding.rvUserDetail.layoutManager = GridLayoutManager(activity, 2)
        val mRealm = DatabaseService().realmInstance
        db = UserProfileDbHandler(requireActivity())
        user = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
        return fragmentUserDetailBinding.root
    }

    data class Detail(val title: String, val description: String)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        user?.let { user ->
            val list = getList(user, db)
            fragmentUserDetailBinding.rvUserDetail.adapter = object : RecyclerView.Adapter<ViewHolderUserDetail>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUserDetail {
                    itemTitleDescBinding = ItemTitleDescBinding.inflate(LayoutInflater.from(activity), parent, false)
                    return ViewHolderUserDetail(itemTitleDescBinding)
                }

                override fun onBindViewHolder(holder: ViewHolderUserDetail, position: Int) {
                    val detail = list[position]
                    itemTitleDescBinding.tvTitle.text = detail.title
                    itemTitleDescBinding.tvDescription.text = detail.description
                }

                override fun getItemCount() = list.size
            }
        } ?: run {
            Utilities.toast(activity, getString(R.string.user_not_available_in_our_database))
        }
    }

    private fun getList(user: RealmUserModel, db: UserProfileDbHandler?): List<Detail> {
        val list: MutableList<Detail> = ArrayList()
        list.add(Detail("Full Name", user.getFullName()))
        list.add(Detail("DOB", getFormatedDate(user.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))
        list.add(Detail("Email", user.email!!))
        list.add(Detail("Phone", user.phoneNumber!!))
        list.add(Detail("Language", user.language!!))
        list.add(Detail("Level", user.level!!))
        list.add(Detail("Number of Visits", db!!.offlineVisits.toString() + ""))
        list.add(Detail("Last Login", Utilities.getRelativeTime(db.lastVisit!!)))
        return list
    }

    class ViewHolderUserDetail(itemTitleDescBinding: ItemTitleDescBinding) : RecyclerView.ViewHolder(itemTitleDescBinding.root)
}