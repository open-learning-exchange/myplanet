package org.ole.planet.myplanet.ui.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList
import java.util.Calendar
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AddMeetupBinding
import org.ole.planet.myplanet.databinding.FragmentEventsDetailBinding
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMeetup.Companion.getHashMap
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.Constants.showBetaFeature
import org.ole.planet.myplanet.utils.TimeUtils

@AndroidEntryPoint
class EventsDetailFragment : Fragment(), View.OnClickListener {
    private var _binding: FragmentEventsDetailBinding? = null
    private val binding get() = _binding!!
    private var meetups: RealmMeetup? = null
    private val viewModel: EventsDetailViewModel by viewModels()
    private var meetUpId: String? = null
    var user: RealmUser? = null
    private var listUsers: ListView? = null
    private var listDesc: ListView? = null
    private var tvJoined: TextView? = null

    private var editStartDate: Long = 0
    private var editEndDate: Long = 0
    private var editStartTime: String = ""
    private var editEndTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            meetUpId = requireArguments().getString("id")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventsDetailBinding.inflate(inflater, container, false)
        listDesc = binding.root.findViewById(R.id.list_desc)
        listUsers = binding.root.findViewById(R.id.list_users)
        tvJoined = binding.root.findViewById(R.id.tv_joined)
        binding.btnEdit.visibility = View.VISIBLE
        binding.btnInvite.visibility = if (showBetaFeature(Constants.KEY_MEETUPS, requireContext())) View.VISIBLE else View.GONE
        binding.btnLeave.visibility = if (showBetaFeature(Constants.KEY_MEETUPS, requireContext())) View.VISIBLE else View.GONE
        binding.btnLeave.setOnClickListener(this)
        binding.btnEdit.setOnClickListener { showEditDialog() }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            user = viewModel.userSessionManager.getUserModel()
            meetups = meetUpId?.takeIf { it.isNotBlank() }?.let { viewModel.eventsRepository.getMeetupByLocalId(it) }
            meetups?.let { setUpData(it) }
            updateAttendanceButton()
            val members = meetUpId?.takeIf { it.isNotBlank() }?.let { viewModel.eventsRepository.getJoinedMembers(it) }.orEmpty()
            setUserList(members)
        }
    }

    private fun showEditDialog() {
        val meetup = meetups ?: return
        val dialogBinding = AddMeetupBinding.inflate(LayoutInflater.from(requireContext()))

        dialogBinding.tvTitle.text = getString(R.string.edit_meetup)

        dialogBinding.etTitle.setText(meetup.title)
        dialogBinding.etDescription.setText(meetup.description)
        dialogBinding.etLocation.setText(meetup.meetupLocation)
        dialogBinding.etLink.setText(meetup.meetupLink)

        editStartDate = meetup.startDate
        editEndDate = meetup.endDate
        editStartTime = meetup.startTime ?: ""
        editEndTime = meetup.endTime ?: ""

        dialogBinding.tvStartDate.text = if (editStartDate > 0)
            TimeUtils.getFormattedDate(editStartDate) else getString(R.string.click_here_to_pick_date)
        dialogBinding.tvEndDate.text = if (editEndDate > 0)
            TimeUtils.getFormattedDate(editEndDate) else getString(R.string.click_here_to_pick_date)
        dialogBinding.tvStartTime.text = editStartTime.ifEmpty { getString(R.string.click_here_to_pick_time) }
        dialogBinding.tvEndTime.text = editEndTime.ifEmpty { getString(R.string.click_here_to_pick_time) }

        when (meetup.recurring) {
            "daily" -> dialogBinding.rgRecuring.check(R.id.rb_daily)
            "weekly" -> dialogBinding.rgRecuring.check(R.id.rb_weekly)
            else -> dialogBinding.rgRecuring.check(R.id.rb_none)
        }

        dialogBinding.tvStartDate.setOnClickListener { pickDate { ts ->
            editStartDate = ts
            dialogBinding.tvStartDate.text = TimeUtils.getFormattedDate(ts)
        }}
        dialogBinding.tvEndDate.setOnClickListener { pickDate { ts ->
            editEndDate = ts
            dialogBinding.tvEndDate.text = TimeUtils.getFormattedDate(ts)
        }}
        dialogBinding.tvStartTime.setOnClickListener { pickTime { t ->
            editStartTime = t
            dialogBinding.tvStartTime.text = t
        }}
        dialogBinding.tvEndTime.setOnClickListener { pickTime { t ->
            editEndTime = t
            dialogBinding.tvEndTime.text = t
        }}

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSave.setOnClickListener {
            val newTitle = dialogBinding.etTitle.text.toString().trim()
            if (newTitle.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.title_is_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val recurring = when (dialogBinding.rgRecuring.checkedRadioButtonId) {
                R.id.rb_daily -> "daily"
                R.id.rb_weekly -> "weekly"
                else -> "none"
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val success = viewModel.eventsRepository.updateMeetup(
                    meetupId = meetup.id ?: return@launch,
                    title = newTitle,
                    description = dialogBinding.etDescription.text.toString().trim(),
                    startDate = editStartDate,
                    endDate = editEndDate,
                    startTime = editStartTime,
                    endTime = editEndTime,
                    meetupLocation = dialogBinding.etLocation.text.toString().trim(),
                    meetupLink = dialogBinding.etLink.text.toString().trim(),
                    recurring = recurring
                )
                if (success) {
                    meetups = viewModel.eventsRepository.getMeetupByLocalId(meetup.id ?: return@launch)
                    meetups?.let { setUpData(it) }
                    dialog.dismiss()
                    Toast.makeText(requireContext(), getString(R.string.meetup_updated), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.meetup_not_updated), Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun pickDate(onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            cal.set(y, m, d, 0, 0, 0)
            onPicked(cal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime(onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hour, minute ->
            onPicked(String.format("%02d:%02d", hour, minute))
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun setUserList(users: List<RealmUser>) {
        listUsers?.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, users)
        val joinedText = if (users.isEmpty()) {
            """(0) ${getString(R.string.no_members_has_joined_this_meet_up)}"""
        } else {
            users.size.toString()
        }
        tvJoined?.text = String.format(getString(R.string.joined_members_colon) + " %s", joinedText)
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
                (convertedView?.findViewById<View>(R.id.title) as TextView).text =
                    context.getString(R.string.message_placeholder, "${getItem(position)} : ")
                (convertedView.findViewById<View>(R.id.description) as TextView).text =
                    context.getString(R.string.message_placeholder, map[getItem(position)])
                return convertedView
            }
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.btn_leave) leaveJoinMeetUp()
    }

    private fun leaveJoinMeetUp() {
        val meetupId = meetUpId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            meetups = viewModel.eventsRepository.toggleAttendance(meetupId, user?.id)
            updateAttendanceButton()
            val members = viewModel.eventsRepository.getJoinedMembers(meetupId)
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

    override fun onDestroy() {
        super.onDestroy()
    }
}