package org.ole.planet.myplanet.ui.health

import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Sort
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentVitalSignBinding
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.user.BecomeMemberActivity
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class MyHealthFragment : Fragment() {

    @Inject
    lateinit var userSessionManager: UserSessionManager

    private val viewModel: HealthViewModel by viewModels()

    private var _binding: FragmentVitalSignBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertMyPersonalBinding: AlertMyPersonalBinding
    private var alertHealthListBinding: AlertHealthListBinding? = null
    var userId: String? = null
    var userModel: RealmUser? = null
    lateinit var adapter: HealthUsersAdapter
    private lateinit var healthAdapter: HealthExaminationAdapter
    var dialog: AlertDialog? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    lateinit var settings: SharedPreferences
    private var textWatcher: TextWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        settings = requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        viewModel.startHealthSync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVitalSignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))

        alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context))
        binding.txtDob.hint = "dd-MM-yyyy"

        val allowDateEdit = false
        if(allowDateEdit) {
            binding.txtDob.setOnClickListener {
                val now = Calendar.getInstance()
                val dpd = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                    val selectedDate =
                        String.format(Locale.US, "%02d-%02d-%04d", dayOfMonth, month + 1, year)
                    binding.txtDob.text = selectedDate
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
                dpd.show()
            }
        } else {
            disableDobField()
        }

        binding.rvRecords.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))

        adapter = HealthUsersAdapter()

        setupObservers()
        setupButtons()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.canAddPatient.collect { canAdd ->
                binding.btnnewPatient.visibility = if (canAdd) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedUser.collect { user ->
                userModel = user
                userId = if (user?._id.isNullOrEmpty()) user?.id else user?._id

                if (user != null) {
                    binding.lblHealthName.text = user.getFullName() ?: getString(R.string.empty_text)
                    binding.txtDob.text = if (TextUtils.isEmpty(user.dob)) getString(R.string.birth_date) else TimeUtils.formatDateToDDMMYYYY(user.dob)

                    binding.addNewRecord.setOnClickListener {
                        startActivity(Intent(activity, AddExaminationActivity::class.java).putExtra("userId", userId))
                    }
                    binding.updateHealth.setOnClickListener {
                        startActivity(Intent(activity, AddHealthActivity::class.java).putExtra("userId", userId))
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.healthRecord.collect { record ->
                showRecords(record)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncStatus.collect { status ->
                 when (status) {
                    is SyncManager.SyncStatus.Syncing -> {
                        if (customProgressDialog == null && isAdded) {
                            customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                            customProgressDialog?.setText(getString(R.string.syncing_health_data))
                            customProgressDialog?.show()
                        }
                    }
                    is SyncManager.SyncStatus.Success -> {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        prefManager.setHealthSynced(true)
                    }
                    is SyncManager.SyncStatus.Error -> {
                         customProgressDialog?.dismiss()
                         customProgressDialog = null
                         if (isAdded) {
                            Snackbar.make(binding.root, "Sync failed: ${status.message}", Snackbar.LENGTH_LONG).setAction("Retry") { viewModel.startHealthSync() }.show()
                         }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnnewPatient.setOnClickListener {
            if (viewModel.canAddPatient.value) {
                selectPatient()
            }
        }
        binding.updateHealth.visibility = View.VISIBLE
    }

    private fun selectPatient() {
        viewModel.loadUsers("joinDate", Sort.DESCENDING)

        viewLifecycleOwner.lifecycleScope.launch {
            showPatientDialog()
        }
    }

    private fun showPatientDialog() {
        alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(context))
        adapter = HealthUsersAdapter { selected ->
            viewModel.selectUser(if (selected._id.isNullOrEmpty()) selected.id else selected._id)
            dialog?.dismiss()
        }

        alertHealthListBinding?.let { binding ->
            binding.list.layoutManager = LinearLayoutManager(requireContext())
            binding.list.adapter = adapter

            // Observer for users list
            val job = viewLifecycleOwner.lifecycleScope.launch {
                viewModel.usersList.collect { users ->
                    adapter.submitList(users)
                    // If list updates, assume loading is done for search
                    binding.searchProgress.visibility = View.GONE
                    binding.list.visibility = View.VISIBLE
                    binding.btnAddMember.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                }
            }

            setTextWatcher(binding.etSearch, binding.btnAddMember, binding.list)
            sortList(binding.spnSort, binding.list)

            binding.btnAddMember.setOnClickListener {
                startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
            }

            dialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                .setTitle(getString(R.string.select_health_member))
                .setView(binding.root)
                .setCancelable(false)
                .setNegativeButton(R.string.dismiss, null)
                .create()

            dialog?.setOnDismissListener { job.cancel() }
            dialog?.show()
        }
    }

    private fun sortList(spnSort: AppCompatSpinner, rv: RecyclerView) {
        spnSort.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                 val (sortBy, sort) = when (p2) {
                    0 -> "joinDate" to Sort.DESCENDING
                    1 -> "joinDate" to Sort.ASCENDING
                    2 -> "name" to Sort.ASCENDING
                    else -> "name" to Sort.DESCENDING
                }
                viewModel.loadUsers(sortBy, sort)
            }
        }
    }

    private fun setTextWatcher(etSearch: EditText, btnAddMember: Button, rv: RecyclerView) {
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                 alertHealthListBinding?.searchProgress?.visibility = View.VISIBLE
                 rv.visibility = View.GONE
                 viewModel.searchUsers(editable.toString(), "joinDate", Sort.DESCENDING)
            }
        }
        etSearch.addTextChangedListener(textWatcher)
    }

    private fun showRecords(healthRecord: HealthRecord?) {
        if (!isAdded || _binding == null) return

        val currentUser = userModel
        if (currentUser == null || userId.isNullOrEmpty() || healthRecord == null) {
            binding.layoutUserDetail.visibility = View.GONE
            binding.tvMessage.visibility = View.VISIBLE
            binding.tvMessage.text = getString(R.string.health_record_not_available)
            binding.txtOtherNeed.text = getString(R.string.empty_text)
            binding.txtSpecialNeeds.text = getString(R.string.empty_text)
            binding.txtBirthPlace.text = getString(R.string.empty_text)
            binding.txtEmergencyContact.text = getString(R.string.empty_text)
            binding.rvRecords.adapter = null
            binding.rvRecords.visibility = View.GONE
            binding.tvNoRecords.visibility = View.VISIBLE
            binding.tvDataPlaceholder.visibility = View.GONE
            return
        }

        binding.layoutUserDetail.visibility = View.VISIBLE
        binding.tvMessage.visibility = View.GONE
        binding.txtFullName.text = getString(R.string.three_strings, currentUser.firstName, currentUser.middleName, currentUser.lastName)
        binding.txtEmail.text = Utilities.checkNA(currentUser.email)
        binding.txtLanguage.text = Utilities.checkNA(currentUser.language)
        binding.txtDob.text = TimeUtils.formatDateToDDMMYYYY(currentUser.dob).ifEmpty { getString(R.string.empty_text) }

        val (mh, mm, list, userMap) = healthRecord
        val myHealths = mm.profile
        binding.txtOtherNeed.text = Utilities.checkNA(myHealths?.notes)
        binding.txtSpecialNeeds.text = Utilities.checkNA(myHealths?.specialNeeds)
        binding.txtBirthPlace.text = Utilities.checkNA(currentUser.birthPlace)
        val contact = myHealths?.emergencyContact?.takeIf { it.isNotBlank() }
        binding.txtEmergencyContact.text = getString(
            R.string.emergency_contact_details,
            Utilities.checkNA(myHealths?.emergencyContactName),
            Utilities.checkNA(myHealths?.emergencyContactType),
            Utilities.checkNA(contact)
        ).trimIndent()

        if (list.isNotEmpty()) {
            binding.rvRecords.visibility = View.VISIBLE
            binding.tvNoRecords.visibility = View.GONE
            binding.tvDataPlaceholder.visibility = View.VISIBLE

            if (!::healthAdapter.isInitialized) {
                healthAdapter = HealthExaminationAdapter(requireActivity(), mh, currentUser, userMap)
            } else {
                healthAdapter.updateData(mh, currentUser, userMap)
            }
            binding.rvRecords.apply {
                layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                isNestedScrollingEnabled = false
                adapter = healthAdapter
            }
            healthAdapter.submitList(list)
            binding.rvRecords.post {
                val lastPosition = list.size - 1
                if (lastPosition >= 0) {
                    binding.rvRecords.scrollToPosition(lastPosition)
                }
            }
        } else {
            binding.rvRecords.visibility = View.GONE
            binding.tvNoRecords.visibility = View.GONE
            binding.tvDataPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun disableDobField() {
        binding.txtDob.isClickable = false
        binding.txtDob.isFocusable = false
        binding.txtDob.setOnClickListener(null)
    }

    override fun onDestroyView() {
        alertHealthListBinding?.etSearch?.removeTextChangedListener(textWatcher)
        textWatcher = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }
}
