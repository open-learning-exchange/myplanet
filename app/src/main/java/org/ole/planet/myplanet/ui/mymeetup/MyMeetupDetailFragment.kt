package org.ole.planet.myplanet.ui.mymeetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.util.ArrayList
import java.util.HashMap
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMyMeetupDetailBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMeetup.Companion.getHashMap
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.MeetupRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature

@AndroidEntryPoint
class MyMeetupDetailFragment : Fragment(), View.OnClickListener {
    private var _binding: FragmentMyMeetupDetailBinding? = null
    private val binding get() = _binding!!
    private var meetups: RealmMeetup? = null
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var meetupRepository: MeetupRepository
    private var meetUpId: String? = null
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
        user = userProfileDbHandler.getUserModelCopy()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            meetups = meetUpId?.takeIf { it.isNotBlank() }?.let { meetupRepository.getMeetupById(it) }
            meetups?.let { setUpData(it) }
            updateAttendanceButton()
            val members = meetUpId?.takeIf { it.isNotBlank() }?.let { meetupRepository.getJoinedMembers(it) }.orEmpty()
            setUserList(members)
        }
    }

    private fun setUserList(users: List<RealmUserModel>) {
        listUsers?.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, users)
        val joinedText = if (users.isEmpty()) {
            """(0) ${getString(R.string.no_members_has_joined_this_meet_up)}"""
        } else {
            users.size.toString()
        }
        tvJoined?.text = String.format(
            getString(R.string.joined_members_colon) + " %s",
            joinedText
        )
    }

    private fun setUpData(meetup: RealmMeetup) {
        binding.meetupTitle.text = meetup.title
        val map: HashMap<String, String> = getHashMap(meetup)
        val keys = ArrayList(map.keys)
        listDesc?.adapter = object : ArrayAdapter<String?>(requireActivity(), R.layout.row_description, keys) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertedView = convertView
                if (convertedView == null) {
                    convertedView = LayoutInflater.from(activity).inflate(R.layout.row_description, parent, false)
                }
                (convertedView?.findViewById<View>(R.id.title) as TextView).text = context.getString(R.string.message_placeholder, "${getItem(position)} : ")
                (convertedView.findViewById<View>(R.id.description) as TextView).text = context.getString(R.string.message_placeholder, map[getItem(position)])
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
        val meetupId = meetUpId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            meetups = meetupRepository.toggleAttendance(meetupId, user?.id)
            updateAttendanceButton()
            val members = meetupRepository.getJoinedMembers(meetupId)
            setUserList(members)
        }
    }

    private fun updateAttendanceButton() {
        val isJoined = !meetups?.userId.isNullOrEmpty()
        binding.btnLeave.setText(if (isJoined) R.string.leave else R.string.join)
        binding.btnLeave.isEnabled = user?.id?.isNotBlank() == true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
