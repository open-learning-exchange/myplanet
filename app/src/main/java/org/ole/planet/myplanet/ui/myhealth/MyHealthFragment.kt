package org.ole.planet.myplanet.ui.myhealth

import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import kotlinx.android.synthetic.main.alert_health_list.view.*
import kotlinx.android.synthetic.main.fragment_vital_sign.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities

/**
 * A simple [Fragment] subclass.
 */
class MyHealthFragment : Fragment() {
    var profileDbHandler: UserProfileDbHandler? = null
    var userId: String? = null
    var mRealm: Realm? = null
    var userModel: RealmUserModel? = null
    lateinit var userModelList: List<RealmUserModel>
    lateinit var adapter: UserListArrayAdapter
    var dialog: AlertDialog? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_vital_sign, container, false)
        mRealm = DatabaseService(activity).realmInstance
        return v
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val v = layoutInflater.inflate(R.layout.alert_users_spinner, null)

        rv_records!!.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        profileDbHandler = UserProfileDbHandler(v.context)
        userId = if (TextUtils.isEmpty(profileDbHandler!!.userModel._id)) profileDbHandler!!.userModel.id else profileDbHandler!!.userModel._id
        getHealthRecords(userId)

        Utilities.log("ROLE " +  profileDbHandler?.userModel?.roleAsString!!)
        if (profileDbHandler?.userModel?.roleAsString!!.contains("health", true)) {
//            btnnew_patient.visibility = if (Constants.showBetaFeature(Constants.KEY_HEALTHWORKER, activity)) View.VISIBLE else View.GONE
            btnnew_patient.visibility =  View.VISIBLE
            btnnew_patient.setOnClickListener { selectPatient() }
            fab_add_member.show(true)
            fab_add_member.visibility = View.VISIBLE
        } else {
            btnnew_patient.visibility = View.GONE
            fab_add_member.hide(true)
            fab_add_member.visibility = View.GONE
        }
        fab_add_member.setOnClickListener { startActivity(Intent(activity, BecomeMemberActivity::class.java)) }

    }

    private fun getHealthRecords(memberId: String?) {
        userId = memberId
        userModel = mRealm!!.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
        lblHealthName!!.text = userModel!!.fullName
        add_new_record.setOnClickListener { startActivity(Intent(activity, AddExaminationActivity::class.java).putExtra("userId", userId)) }
        update_health.setOnClickListener { startActivity(Intent(activity, AddMyHealthActivity::class.java).putExtra("userId", userId)) }
        showRecords()
    }

    private fun selectPatient() {
        userModelList = mRealm!!.where(RealmUserModel::class.java).sort("joinDate", Sort.DESCENDING).findAll()
        adapter = UserListArrayAdapter(activity!!, android.R.layout.simple_list_item_1, userModelList)
        val alertHealth = LayoutInflater.from(activity).inflate(R.layout.alert_health_list, null)
        val btnAddMember = alertHealth.btn_add_member
        val etSearch = alertHealth.et_search
        val spnSort = alertHealth.spn_sort
        btnAddMember.setOnClickListener { startActivity(Intent(requireContext(), BecomeMemberActivity::class.java)) }
        val lv = alertHealth.list
        setTextWatcher(etSearch, btnAddMember, lv)
        lv.adapter = adapter
        lv.onItemClickListener = OnItemClickListener { adapterView: AdapterView<*>?, view: View, i: Int, l: Long ->
            val selected = lv.adapter.getItem(i) as RealmUserModel
            userId = if (selected._id.isNullOrEmpty()) selected.id else selected._id
            getHealthRecords(userId)
            dialog!!.dismiss()
        }
        sortList(spnSort, lv);
        dialog = AlertDialog.Builder(activity!!).setTitle(getString(R.string.select_health_member)).setView(alertHealth).setCancelable(false).setNegativeButton("Dismiss", null).create()
        dialog?.show()
    }

    private fun sortList(spnSort: AppCompatSpinner, lv: ListView) {
        spnSort.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {
            }

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val sort: Sort
                val sortBy: String
                when {
                    p2 == 0 -> {
                        sortBy = "joinDate"
                        sort = Sort.DESCENDING
                    }
                    p2 == 1 -> {
                        sortBy = "joinDate"
                        sort = Sort.ASCENDING
                    }
                    p2 == 2 -> {
                        sortBy = "name"
                        sort = Sort.ASCENDING
                    }
                    else -> {
                        sortBy = "name"
                        sort = Sort.DESCENDING
                    }
                }
                userModelList = mRealm!!.where(RealmUserModel::class.java).sort(sortBy, sort).findAll()
                adapter = UserListArrayAdapter(activity!!, android.R.layout.simple_list_item_1, userModelList)
                lv.adapter = adapter
            }
        }
    }

    private fun setTextWatcher(etSearch: EditText, btnAddMember: Button, lv: ListView) {
        var timer: CountDownTimer? = null
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {


            }

            override fun afterTextChanged(editable: Editable) {
                timer?.cancel()
                timer = object : CountDownTimer(1000, 1500) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        val userModelList = mRealm!!.where(RealmUserModel::class.java)
                                .contains("firstName", editable.toString(), Case.INSENSITIVE)
                                .or()
                                .contains("lastName", editable.toString(), Case.INSENSITIVE)
                                .or()
                                .contains("name", editable.toString(), Case.INSENSITIVE)
                                .sort("joinDate", Sort.DESCENDING).findAll()

                        val adapter = UserListArrayAdapter(activity!!, android.R.layout.simple_list_item_1, userModelList)
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
        layout_user_detail.visibility = View.VISIBLE
        tv_message.visibility = View.GONE
        txt_full_name.text = """${userModel?.firstName} ${userModel?.middleName} ${userModel?.lastName}"""
        txt_email.text = Utilities.checkNA(userModel!!.email)
        txt_language.text = Utilities.checkNA(userModel!!.language)
        txt_dob.text = Utilities.checkNA(userModel!!.dob)
        var mh = mRealm!!.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
        if (mh == null) {
            mh = mRealm!!.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
        }
        if (mh != null) {
            val mm = getHealthProfile(mh)
            if (mm == null) {
                rv_records.adapter = null
                Utilities.toast(activity, "Health Record not available.")
                return
            }
            val myHealths = mm.profile
            txt_other_need.text = Utilities.checkNA(myHealths.notes)
            txt_special_needs.text = Utilities.checkNA(myHealths.specialNeeds)
            txt_birth_place.text = Utilities.checkNA(userModel?.birthPlace)
            txt_emergency_contact!!.text = """
                Name : ${Utilities.checkNA(myHealths.emergencyContactName)}
                Type : ${Utilities.checkNA(myHealths.emergencyContactName)}
                Contact : ${Utilities.checkNA(myHealths.emergencyContact)}
                """.trimIndent()
            val list = getExaminations(mm)

            val adap = AdapterHealthExamination(activity, list, mh, userModel)
            adap.setmRealm(mRealm)
            rv_records.apply {
                layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                isNestedScrollingEnabled = false
                adapter = adap
            }
            rv_records.post { rv_records!!.scrollToPosition(list!!.size - 1) }
        } else {
            txt_other_need!!.text = ""
            txt_special_needs!!.text = ""
            txt_birth_place!!.text = ""
            txt_emergency_contact!!.text = ""
            rv_records!!.adapter = null
        }
    }

    private fun getExaminations(mm: RealmMyHealth): List<RealmMyHealthPojo> {
        var healths = mRealm?.where(RealmMyHealthPojo::class.java)!!.findAll()
        healths.forEach {
            Utilities.log(it.profileId)
        }
        healths = mRealm?.where(RealmMyHealthPojo::class.java)!!.equalTo("profileId", mm.userKey)!!.findAll()
        return healths!!
    }

    private fun getHealthProfile(mh: RealmMyHealthPojo): RealmMyHealth? {
        Utilities.log(mh.data)
        val json = AndroidDecrypter.decrypt(mh.data, userModel!!.key, userModel!!.iv)
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
}