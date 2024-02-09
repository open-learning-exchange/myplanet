package org.ole.planet.myplanet.ui.mymeetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMyMeetupDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMeetup.Companion.getHashMap
import org.ole.planet.myplanet.model.RealmMeetup.Companion.getJoinedUserIds
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature

class MyMeetupDetailFragment : Fragment(), View.OnClickListener {
    private lateinit var fragmentMyMeetupDetailBinding: FragmentMyMeetupDetailBinding
    private var meetups: RealmMeetup? = null
   lateinit var mRealm: Realm
    private var meetUpId: String? = null
    var profileDbHandler: UserProfileDbHandler? = null
    var user: RealmUserModel? = null
    private var listUsers: ListView? = null
    private var listDesc: ListView? = null
    private var tvJoined: TextView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            meetUpId = requireArguments().getString("id")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMyMeetupDetailBinding = FragmentMyMeetupDetailBinding.inflate(inflater, container, false)
        listDesc = fragmentMyMeetupDetailBinding.root.findViewById(R.id.list_desc)
        listUsers = fragmentMyMeetupDetailBinding.root.findViewById(R.id.list_users)
        tvJoined = fragmentMyMeetupDetailBinding.root.findViewById(R.id.tv_joined)
        fragmentMyMeetupDetailBinding.btnInvite.visibility = if (showBetaFeature(Constants.KEY_MEETUPS, requireContext())) View.VISIBLE else View.GONE
        fragmentMyMeetupDetailBinding.btnLeave.visibility = if (showBetaFeature(Constants.KEY_MEETUPS, requireContext())) View.VISIBLE else View.GONE
        fragmentMyMeetupDetailBinding.btnLeave.setOnClickListener(this)
        mRealm = DatabaseService(requireActivity()).realmInstance
        profileDbHandler = UserProfileDbHandler(requireContext())
        user = mRealm.copyFromRealm(profileDbHandler!!.userModel)
        return fragmentMyMeetupDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        meetups = mRealm.where(RealmMeetup::class.java).equalTo("meetupId", meetUpId).findFirst()
        setUpData()
        setUserList()
    }

    private fun setUserList() {
        val ids = getJoinedUserIds(mRealm)
        val users = mRealm.where(RealmUserModel::class.java).`in`("id", ids).findAll()
        listUsers!!.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, users)
        tvJoined!!.text = String.format(getString(R.string.joined_members_colon) + " %s", if (users.size == 0) """(0) ${getString(R.string.no_members_has_joined_this_meet_up)}""" else users.size)
    }

    private fun setUpData() {
        fragmentMyMeetupDetailBinding.meetupTitle.text = meetups!!.title
        val map: HashMap<String, String> = getHashMap(meetups!!)
        val keys = ArrayList(map.keys)
        listDesc!!.adapter = object : ArrayAdapter<String?>(requireActivity(), R.layout.row_description, keys) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertView = convertView
                if (convertView == null) {
                    convertView = LayoutInflater.from(activity).inflate(R.layout.row_description, parent, false)
                }
                (convertView!!.findViewById<View>(R.id.title) as TextView).text = "${getItem(position)} : "
                (convertView.findViewById<View>(R.id.description) as TextView).text = "${map[getItem(position)]}"
                return convertView
            }
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.btn_leave) {
            leaveJoinMeetUp()
        }
    }

    private fun leaveJoinMeetUp() {
        mRealm.executeTransaction {
            if (meetups!!.userId!!.isEmpty()) {
                meetups!!.userId = user!!.id
                fragmentMyMeetupDetailBinding.btnLeave.setText(R.string.leave)
            } else {
                meetups!!.userId = ""
                fragmentMyMeetupDetailBinding.btnLeave.setText(R.string.join)
            }
        }
    }
}
