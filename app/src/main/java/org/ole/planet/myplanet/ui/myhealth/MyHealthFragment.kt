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
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentVitalSignBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.getFormatedDate
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Calendar
import java.util.Locale

class MyHealthFragment : Fragment() {
    private lateinit var fragmentVitalSignBinding: FragmentVitalSignBinding
    private lateinit var alertMyPersonalBinding: AlertMyPersonalBinding
    private lateinit var alertHealthListBinding: AlertHealthListBinding
    var profileDbHandler: UserProfileDbHandler? = null
    var userId: String? = null
    lateinit var mRealm: Realm
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
        fragmentVitalSignBinding = FragmentVitalSignBinding.inflate(inflater, container, false)
        mRealm = DatabaseService(requireContext()).realmInstance
        return fragmentVitalSignBinding.root
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
        SyncManager.instance?.start(object : SyncListener {
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

            override fun onSyncFailed(message: String?) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        Snackbar.make(fragmentVitalSignBinding.root, "Sync failed: ${message ?: "Unknown error"}", Snackbar.LENGTH_LONG).setAction("Retry") { startHealthSync() }.show()
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
        fragmentVitalSignBinding.txtDob.hint = "yyyy-MM-dd'"

        val allowDateEdit = false
        if(allowDateEdit) {
            fragmentVitalSignBinding.txtDob.setOnClickListener {
                val now = Calendar.getInstance()
                val dpd = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                    val selectedDate =
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    fragmentVitalSignBinding.txtDob.text = selectedDate
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
                dpd.show()
            }
        } else {
            disableDobField()
        }

        fragmentVitalSignBinding.rvRecords.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))

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
        fragmentVitalSignBinding.btnnewPatient.visibility =
            if (isHealthProvider) View.VISIBLE else View.GONE

        fragmentVitalSignBinding.btnnewPatient.setOnClickListener {
            if (isHealthProvider) {
                selectPatient()
            }
        }
        fragmentVitalSignBinding.updateHealth.visibility = View.VISIBLE

        fragmentVitalSignBinding.updateHealth.setOnClickListener {
            startActivity(Intent(activity, AddMyHealthActivity::class.java).putExtra("userId", userId))
        }

        fragmentVitalSignBinding.txtDob.text = if (TextUtils.isEmpty(userModel?.dob)) getString(R.string.birth_date) else getFormatedDate(userModel?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    private fun getHealthRecords(memberId: String?) {
        userId = memberId
        userModel = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
        fragmentVitalSignBinding.lblHealthName.text = userModel?.getFullName()
        fragmentVitalSignBinding.addNewRecord.setOnClickListener {
            startActivity(Intent(activity, AddExaminationActivity::class.java).putExtra("userId", userId))
        }
        fragmentVitalSignBinding.updateHealth.setOnClickListener {
            startActivity(Intent(activity, AddMyHealthActivity::class.java).putExtra("userId", userId))
        }
        showRecords()
    }

    private fun selectPatient() {
        userModelList = mRealm.where(RealmUserModel::class.java).sort("joinDate", Sort.DESCENDING).findAll()
        adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
        alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(context))
        alertHealthListBinding.btnAddMember.setOnClickListener {
            startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
        }

        setTextWatcher(alertHealthListBinding.etSearch, alertHealthListBinding.btnAddMember, alertHealthListBinding.list)
        alertHealthListBinding.list.adapter = adapter
        alertHealthListBinding.list.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, _: View, i: Int, _: Long ->
            val selected = alertHealthListBinding.list.adapter.getItem(i) as RealmUserModel
            userId = if (selected._id.isNullOrEmpty()) selected.id else selected._id
            getHealthRecords(userId)
            dialog?.dismiss()
        }
        sortList(alertHealthListBinding.spnSort, alertHealthListBinding.list)
        dialog = AlertDialog.Builder(requireActivity(),R.style.AlertDialogTheme)
            .setTitle(getString(R.string.select_health_member)).setView(alertHealthListBinding.root)
            .setCancelable(false).setNegativeButton(R.string.dismiss, null).create()
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
                userModelList = mRealm.where(RealmUserModel::class.java).sort(sortBy, sort).findAll()
                adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
                lv.adapter = adapter
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
                        val userModelList = mRealm.where(RealmUserModel::class.java)
                            .contains("firstName", editable.toString(), Case.INSENSITIVE).or()
                            .contains("lastName", editable.toString(), Case.INSENSITIVE).or()
                            .contains("name", editable.toString(), Case.INSENSITIVE)
                            .sort("joinDate", Sort.DESCENDING).findAll()

                        val adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
                        lv.adapter = adapter
                        btnAddMember.visibility = if (adapter.count == 0) View.VISIBLE else View.GONE
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
        fragmentVitalSignBinding.layoutUserDetail.visibility = View.VISIBLE
        fragmentVitalSignBinding.tvMessage.visibility = View.GONE
        fragmentVitalSignBinding.txtFullName.text = getString(R.string.three_strings, userModel?.firstName, userModel?.middleName, userModel?.lastName)
        fragmentVitalSignBinding.txtEmail.text = Utilities.checkNA(userModel?.email!!)
        fragmentVitalSignBinding.txtLanguage.text = Utilities.checkNA(userModel?.language!!)
        fragmentVitalSignBinding.txtDob.text = Utilities.checkNA(userModel?.dob!!)
        var mh = mRealm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
        if (mh == null) {
            mh = mRealm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
        }
        if (mh != null) {
            val mm = getHealthProfile(mh)
            if (mm == null) {
                fragmentVitalSignBinding.rvRecords.adapter = null
                fragmentVitalSignBinding.tvNoRecords.visibility = View.VISIBLE
                fragmentVitalSignBinding.tvDataPlaceholder.visibility = View.GONE
                Utilities.toast(activity, getString(R.string.health_record_not_available))
                return
            }
            val myHealths = mm.profile
            fragmentVitalSignBinding.txtOtherNeed.text = Utilities.checkNA(myHealths?.notes)
            fragmentVitalSignBinding.txtSpecialNeeds.text = Utilities.checkNA(myHealths?.specialNeeds)
            fragmentVitalSignBinding.txtBirthPlace.text = Utilities.checkNA(userModel?.birthPlace)
            fragmentVitalSignBinding.txtEmergencyContact.text = getString(R.string.emergency_contact_details,
                Utilities.checkNA(myHealths?.emergencyContactName),
                Utilities.checkNA(myHealths?.emergencyContactType),
                Utilities.checkNA(myHealths?.emergencyContact)).trimIndent()

            val list = getExaminations(mm)

            if (list != null && list.isNotEmpty()) {
                fragmentVitalSignBinding.rvRecords.visibility = View.VISIBLE
                fragmentVitalSignBinding.tvNoRecords.visibility = View.GONE
                fragmentVitalSignBinding.tvDataPlaceholder.visibility = View.VISIBLE

                val adap = AdapterHealthExamination(requireActivity(), list, mh, userModel)
                adap.setmRealm(mRealm)
                fragmentVitalSignBinding.rvRecords.apply {
                    layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                    isNestedScrollingEnabled = false
                    adapter = adap
                }
                fragmentVitalSignBinding.rvRecords.post {
                    val lastPosition = list.size - 1
                    if (lastPosition >= 0) {
                        fragmentVitalSignBinding.rvRecords.scrollToPosition(lastPosition)
                    }
                }
            } else {
                fragmentVitalSignBinding.rvRecords.visibility = View.GONE
                fragmentVitalSignBinding.tvNoRecords.visibility = View.GONE
                fragmentVitalSignBinding.tvDataPlaceholder.visibility = View.VISIBLE
            }
        } else {
            fragmentVitalSignBinding.txtOtherNeed.text = getString(R.string.empty_text)
            fragmentVitalSignBinding.txtSpecialNeeds.text = getString(R.string.empty_text)
            fragmentVitalSignBinding.txtBirthPlace.text = getString(R.string.empty_text)
            fragmentVitalSignBinding.txtEmergencyContact.text = getString(R.string.empty_text)
            fragmentVitalSignBinding.rvRecords.adapter = null
            fragmentVitalSignBinding.rvRecords.visibility = View.GONE
            fragmentVitalSignBinding.tvNoRecords.visibility = View.VISIBLE
            fragmentVitalSignBinding.tvDataPlaceholder.visibility = View.GONE
        }
    }

    private fun getExaminations(mm: RealmMyHealth): List<RealmMyHealthPojo>? {
        val healths = mRealm.where(RealmMyHealthPojo::class.java)?.equalTo("profileId", mm.userKey)?.findAll()
        return healths
    }

    private fun getHealthProfile(mh: RealmMyHealthPojo): RealmMyHealth? {
        val json = AndroidDecrypter.decrypt(mh.data, userModel?.key, userModel?.iv)
        return if (TextUtils.isEmpty(json)) {
            null
        } else {
            try {
                Gson().fromJson(json, RealmMyHealth::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun disableDobField() {
        fragmentVitalSignBinding.txtDob.isClickable = false
        fragmentVitalSignBinding.txtDob.isFocusable = false
        fragmentVitalSignBinding.txtDob.setOnClickListener(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        customProgressDialog?.dismiss()
        customProgressDialog = null
    }
}
