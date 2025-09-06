package org.ole.planet.myplanet.ui.mymeetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import javax.inject.Inject
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

@AndroidEntryPoint
class MyMeetupDetailFragment : Fragment(), View.OnClickListener {
    private var _binding: FragmentMyMeetupDetailBinding? = null
    private val binding get() = _binding!!
    private var meetups: RealmMeetup? = null
    @Inject
    lateinit var databaseService: DatabaseService
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
        _binding = FragmentMyMeetupDetailBinding.inflate(inflater, container, false)
        listDesc = binding.root.findViewById(R.id.list_desc)
        listUsers = binding.root.findViewById(R.id.list_users)
        tvJoined = binding.root.findViewById(R.id.tv_joined)
        binding.btnInvite.visibility = if (showBetaFeature(Constants.KEY_MEETUPS, requireContext())) View.VISIBLE else View.GONE
        binding.btnLeave.visibility = if (showBetaFeature(Constants.KEY_MEETUPS, requireContext())) View.VISIBLE else View.GONE
        binding.btnLeave.setOnClickListener(this)
        mRealm = databaseService.realmInstance
        profileDbHandler = UserProfileDbHandler(requireContext())
        user = profileDbHandler?.userModel?.let { mRealm.copyFromRealm(it) }
        return binding.root
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
        listUsers?.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, users)
        tvJoined?.text = String.format(getString(R.string.joined_members_colon) + " %s", if (users.size == 0) """(0) ${getString(R.string.no_members_has_joined_this_meet_up)}""" else users.size)
    }

    private fun setUpData() {
        binding.meetupTitle.text = meetups?.title
        val map: HashMap<String, String>? = meetups?.let { getHashMap(it) }
        val keys = ArrayList(map?.keys ?: emptyList())
        listDesc?.adapter = object : ArrayAdapter<String?>(requireActivity(), R.layout.row_description, keys) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertedView = convertView
                if (convertedView == null) {
                    convertedView = LayoutInflater.from(activity).inflate(R.layout.row_description, parent, false)
                }
                (convertedView?.findViewById<View>(R.id.title) as TextView).text = context.getString(R.string.message_placeholder, "${getItem(position)} : ")
                (convertedView.findViewById<View>(R.id.description) as TextView).text = context.getString(R.string.message_placeholder, map?.get(getItem(position)))
                return convertedView
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
            if (meetups?.userId?.isEmpty() == true) {
                meetups?.userId = user?.id
                binding.btnLeave.setText(R.string.leave)
            } else {
                meetups?.userId = ""
                binding.btnLeave.setText(R.string.join)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
        super.onDestroy()
    }
}
