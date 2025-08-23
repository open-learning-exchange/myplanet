package org.ole.planet.myplanet.ui.myhealth

import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Sort
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentVitalSignBinding
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.MyHealthRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class MyHealthFragment : Fragment() {
    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var myHealthRepository: MyHealthRepository

    private var _binding: FragmentVitalSignBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertMyPersonalBinding: AlertMyPersonalBinding
    private lateinit var alertHealthListBinding: AlertHealthListBinding
    var profileDbHandler: UserProfileDbHandler? = null
    var userId: String? = null
    var userModel: RealmUserModel? = null
    lateinit var userModelList: List<RealmUserModel>
    lateinit var adapter: UserListArrayAdapter
    var dialog: AlertDialog? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    lateinit var settings: SharedPreferences
    private val serverUrlMapper = ServerUrlMapper()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        settings = requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        startHealthSync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVitalSignBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun startHealthSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isHealthSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                activity?.runOnUiThread {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_health_data))
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        refreshHealthData()
                        prefManager.setHealthSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG).setAction("Retry") { startHealthSync() }.show()
                    }
                }
            }
        }, "full", listOf("health"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    private fun refreshHealthData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            profileDbHandler = UserProfileDbHandler(requireContext())
            userId = if (TextUtils.isEmpty(profileDbHandler?.userModel?._id)) {
                profileDbHandler?.userModel?.id
            } else {
                profileDbHandler?.userModel?._id
            }
            getHealthRecords(userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))
        alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context))
        binding.txtDob.hint = "yyyy-MM-dd'"

        val allowDateEdit = false
        if(allowDateEdit) {
            binding.txtDob.setOnClickListener {
                val now = Calendar.getInstance()
                val dpd = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                    val selectedDate =
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    binding.txtDob.text = selectedDate
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
                dpd.show()
            }
        } else {
            disableDobField()
        }

        binding.rvRecords.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))

        setupInitialData()
        setupButtons()
    }

    private fun setupInitialData() {
        profileDbHandler = UserProfileDbHandler(alertMyPersonalBinding.root.context)
        userId = if (TextUtils.isEmpty(profileDbHandler?.userModel?._id)) profileDbHandler?.userModel?.id else profileDbHandler?.userModel?._id
        getHealthRecords(userId)
    }

    private fun setupButtons() {
        val isHealthProvider = userModel?.rolesList?.contains("health") ?: false
        binding.btnnewPatient.visibility =
            if (isHealthProvider) View.VISIBLE else View.GONE

        binding.btnnewPatient.setOnClickListener {
            if (isHealthProvider) {
                selectPatient()
            }
        }
        binding.updateHealth.visibility = View.VISIBLE

        binding.updateHealth.setOnClickListener {
            startActivity(Intent(activity, AddMyHealthActivity::class.java).putExtra("userId", userId))
        }

        binding.txtDob.text = if (TextUtils.isEmpty(userModel?.dob)) getString(R.string.birth_date) else getFormattedDate(userModel?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    private fun getHealthRecords(memberId: String?) {
        userId = memberId
        viewLifecycleOwner.lifecycleScope.launch {
            userModel = myHealthRepository.getUserModel(userId)
            binding.lblHealthName.text = userModel?.getFullName()
            showRecords()
        }
        binding.addNewRecord.setOnClickListener {
            startActivity(Intent(activity, AddExaminationActivity::class.java).putExtra("userId", userId))
        }
        binding.updateHealth.setOnClickListener {
            startActivity(Intent(activity, AddMyHealthActivity::class.java).putExtra("userId", userId))
        }
    }

    private fun selectPatient() {
        alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(context))
        dialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(getString(R.string.select_health_member)).setView(alertHealthListBinding.root)
            .setCancelable(false).setNegativeButton(R.string.dismiss, null).create()

        viewLifecycleOwner.lifecycleScope.launch {
            userModelList = myHealthRepository.getAllUsers("joinDate", Sort.DESCENDING)
            adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
            alertHealthListBinding.list.adapter = adapter
        }

        alertHealthListBinding.btnAddMember.setOnClickListener {
            startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
        }

        setTextWatcher(alertHealthListBinding.etSearch, alertHealthListBinding.btnAddMember, alertHealthListBinding.list)
        alertHealthListBinding.list.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, _: View, i: Int, _: Long ->
            val selected = alertHealthListBinding.list.adapter.getItem(i) as RealmUserModel
            userId = if (selected._id.isNullOrEmpty()) selected.id else selected._id
            getHealthRecords(userId)
            dialog?.dismiss()
        }
        sortList(alertHealthListBinding.spnSort, alertHealthListBinding.list)
        dialog?.show()
    }

    private fun sortList(spnSort: AppCompatSpinner, lv: ListView) {
        spnSort.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val sort: Sort
                val sortBy: String
                when (p2) {
                    0 -> {
                        sortBy = "joinDate"
                        sort = Sort.DESCENDING
                    }
                    1 -> {
                        sortBy = "joinDate"
                        sort = Sort.ASCENDING
                    }
                    2 -> {
                        sortBy = "name"
                        sort = Sort.ASCENDING
                    }
                    else -> {
                        sortBy = "name"
                        sort = Sort.DESCENDING
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    userModelList = myHealthRepository.getAllUsers(sortBy, sort)
                    adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
                    lv.adapter = adapter
                }
            }
        }
    }

    private fun setTextWatcher(etSearch: EditText, btnAddMember: Button, lv: ListView) {
        var timer: CountDownTimer? = null
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun afterTextChanged(editable: Editable) {
                timer?.cancel()
                timer = object : CountDownTimer(1000, 1500) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val userModelList = myHealthRepository.searchUsers(editable.toString())
                            val adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
                            lv.adapter = adapter
                            btnAddMember.visibility = if (adapter.count == 0) View.VISIBLE else View.GONE
                        }
                    }
                }.start()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        showRecords()
    }

    private fun showRecords() {
        if (userModel == null && userId != null) {
            getHealthRecords(userId)
            return
        } else if (userModel == null) {
            return
        }

        binding.layoutUserDetail.visibility = View.VISIBLE
        binding.tvMessage.visibility = View.GONE
        binding.txtFullName.text = getString(R.string.three_strings, userModel?.firstName, userModel?.middleName, userModel?.lastName)
        binding.txtEmail.text = Utilities.checkNA(userModel?.email ?: "")
        binding.txtLanguage.text = Utilities.checkNA(userModel?.language ?: "")
        binding.txtDob.text = Utilities.checkNA(userModel?.dob ?: "")

        viewLifecycleOwner.lifecycleScope.launch {
            val mh = myHealthRepository.getMyHealthPojo(userId)
            if (mh != null) {
                val mm = myHealthRepository.getMyHealthProfile(mh, userModel)
                if (mm == null) {
                    binding.rvRecords.adapter = null
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.tvDataPlaceholder.visibility = View.GONE
                    Utilities.toast(activity, getString(R.string.health_record_not_available))
                    return@launch
                }
                val myHealths = mm.profile
                binding.txtOtherNeed.text = Utilities.checkNA(myHealths?.notes)
                binding.txtSpecialNeeds.text = Utilities.checkNA(myHealths?.specialNeeds)
                binding.txtBirthPlace.text = Utilities.checkNA(userModel?.birthPlace)
                binding.txtEmergencyContact.text = getString(R.string.emergency_contact_details,
                    Utilities.checkNA(myHealths?.emergencyContactName),
                    Utilities.checkNA(myHealths?.emergencyContactType),
                    Utilities.checkNA(myHealths?.emergencyContact)).trimIndent()

                val list = myHealthRepository.getExaminations(mm.userKey)

                if (list != null && list.isNotEmpty()) {
                    binding.rvRecords.visibility = View.VISIBLE
                    binding.tvNoRecords.visibility = View.GONE
                    binding.tvDataPlaceholder.visibility = View.VISIBLE

                    val adap = AdapterHealthExamination(requireActivity(), list, mh, userModel, myHealthRepository)
                    binding.rvRecords.apply {
                        layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                        isNestedScrollingEnabled = false
                        adapter = adap
                    }
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
            } else {
                binding.txtOtherNeed.text = getString(R.string.empty_text)
                binding.txtSpecialNeeds.text = getString(R.string.empty_text)
                binding.txtBirthPlace.text = getString(R.string.empty_text)
                binding.txtEmergencyContact.text = getString(R.string.empty_text)
                binding.rvRecords.adapter = null
                binding.rvRecords.visibility = View.GONE
                binding.tvNoRecords.visibility = View.VISIBLE
                binding.tvDataPlaceholder.visibility = View.GONE
            }
        }
    }

    private fun disableDobField() {
        binding.txtDob.isClickable = false
        binding.txtDob.isFocusable = false
        binding.txtDob.setOnClickListener(null)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }
}
