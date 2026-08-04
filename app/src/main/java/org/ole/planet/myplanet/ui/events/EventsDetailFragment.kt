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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.HashMap
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AddMeetupBinding
import org.ole.planet.myplanet.databinding.FragmentEventsDetailBinding
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.Meetup.Companion.getHashMap
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.Constants.showBetaFeature
import org.ole.planet.myplanet.utils.TimeUtils
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class EventsDetailFragment : Fragment(), View.OnClickListener {
    private var _binding: FragmentEventsDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EventsDetailViewModel by viewModels()
    private var meetUpId: String? = null
    private var listUsers: ListView? = null
    private var listDesc: RecyclerView? = null
    private var tvJoined: TextView? = null

    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var teamsRepository: TeamsRepository

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

        viewModel.loadData(meetUpId)

        collectWhenStarted(viewModel.meetup) { meetup ->
            meetup?.let { setUpData(it) }
            updateAttendanceButton()
        }

        collectWhenStarted(viewModel.members) { members ->
            setUserList(members)
        }

        collectWhenStarted(viewModel.user) {
            updateAttendanceButton()
        }

        collectWhenStarted(viewModel.updateSuccess) { success ->
            if (success == true) {
                Toast.makeText(requireContext(), getString(R.string.meetup_updated), Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateSuccess()
            } else if (success == false) {
                Toast.makeText(requireContext(), getString(R.string.meetup_not_updated), Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateSuccess()
            }
        }
    }

    private fun showEditDialog() {
        val meetup = viewModel.meetup.value ?: return
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

        dialogBinding.etRecurringCount.setText(meetup.recurringNumber.toString())
        val isRecurringInit = meetup.recurring.equals("daily", ignoreCase = true) || meetup.recurring.equals("weekly", ignoreCase = true)
        dialogBinding.tlRecurringCount.visibility = if (isRecurringInit) View.VISIBLE else View.GONE

        dialogBinding.rgRecuring.setOnCheckedChangeListener { _, checkedId ->
            val isRecurring = checkedId == R.id.rb_daily || checkedId == R.id.rb_weekly
            dialogBinding.tlRecurringCount.visibility = if (isRecurring) View.VISIBLE else View.GONE
        }

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

        lifecycleScope.launch {
            val allowed = canDeleteMeetup(meetup)
            dialogBinding.btnDelete.visibility = if (allowed) View.VISIBLE else View.GONE
        }
        dialogBinding.btnDelete.setOnClickListener {
            dialog.dismiss()
            confirmDeleteMeetup(meetup)
        }

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
            val recurringCountText = dialogBinding.etRecurringCount.text.toString().trim()
            val recurringNumber = recurringCountText.toIntOrNull() ?: 10

            viewModel.updateMeetup(
                meetupId = meetup.id ?: return@setOnClickListener,
                title = newTitle,
                description = dialogBinding.etDescription.text.toString().trim(),
                startDate = editStartDate,
                endDate = editEndDate,
                startTime = editStartTime,
                endTime = editEndTime,
                meetupLocation = dialogBinding.etLocation.text.toString().trim(),
                meetupLink = dialogBinding.etLink.text.toString().trim(),
                recurring = recurring,
                recurringNumber = recurringNumber
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private suspend fun canDeleteMeetup(meetup: Meetup): Boolean {
        val currentUser = userRepository.getUserModel()
        val currentUserId = currentUser?.id
        val currentUserName = currentUser?.name
        if (currentUserId.isNullOrBlank()) return false

        val isCreator = (!meetup.creator.isNullOrBlank() && (meetup.creator == currentUserId || meetup.creator == currentUserName)) ||
                (!meetup.userId.isNullOrBlank() && meetup.userId == currentUserId)
        if (isCreator) return true

        val meetupTeamId = meetup.teamId
        if (!meetupTeamId.isNullOrBlank()) {
            val isLeader = teamsRepository.isTeamLeader(meetupTeamId, currentUserId)
            if (isLeader) return true
        }

        return false
    }

    private fun confirmDeleteMeetup(meetup: Meetup) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setMessage(R.string.confirm_delete_meetup)
            .setPositiveButton(R.string.ok) { _, _ ->
                val targetId = meetup.id.ifEmpty { meetup.meetupId ?: "" }
                viewModel.deleteMeetup(targetId) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), getString(R.string.meetup_deleted), Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.meetup_not_deleted), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    private fun setUserList(users: List<UserEntity>) {
        listUsers?.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, users)
        val joinedText = if (users.isEmpty()) {
            """(0) ${getString(R.string.no_members_has_joined_this_meet_up)}"""
        } else {
            users.size.toString()
        }
        tvJoined?.text = String.format(getString(R.string.joined_members_colon) + " %s", joinedText)
    }

    private fun setUpData(meetup: Meetup) {
        binding.meetupTitle.text = meetup.title
        val map: HashMap<String, String> = getHashMap(meetup)
        val items = map.map { EventsDescriptionAdapter.DescriptionItem(it.key, it.value) }
        val eventsDescriptionAdapter = EventsDescriptionAdapter()
        listDesc?.layoutManager = LinearLayoutManager(requireContext())
        listDesc?.adapter = eventsDescriptionAdapter
        eventsDescriptionAdapter.submitList(items)
    }

    override fun onClick(view: View) {
        if (view.id == R.id.btn_leave) leaveJoinMeetUp()
    }

    private fun leaveJoinMeetUp() {
        val meetupId = meetUpId ?: return
        viewModel.toggleAttendance(meetupId)
    }

    private fun updateAttendanceButton() {
        val meetup = viewModel.meetup.value
        val user = viewModel.user.value

        val currentTime = Calendar.getInstance().timeInMillis
        val endDate = meetup?.endDate ?: 0L
        // endDate is set to midnight of that day. Add 86399999L (23:59:59.999) to cover the whole day.
        val endOfDay = if (endDate > 0) endDate + 86399999L else 0L
        val isEventActive = endOfDay == 0L || currentTime <= endOfDay

        val isJoined = !meetup?.userId.isNullOrEmpty()
        binding.btnLeave.setText(if (isJoined) R.string.leave else R.string.join)
        binding.btnLeave.isEnabled = user?.id?.isNotBlank() == true && isEventActive
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
