package org.ole.planet.myplanet.ui.enterprises

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.borax12.materialdaterangepicker.date.DatePickerDialog
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AddTransactionBinding
import org.ole.planet.myplanet.databinding.FragmentFinanceBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.TimeUtils.formatDateTZ
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class FinanceFragment : BaseTeamFragment() {
    private lateinit var fragmentFinanceBinding: FragmentFinanceBinding
    private lateinit var addTransactionBinding: AddTransactionBinding
    private lateinit var fRealm: Realm
    private var adapterFinance: AdapterFinance? = null
    var date: Calendar? = null
    var list: RealmResults<RealmMyTeam>? = null
    private var isAsc = false

    var listener =
        android.app.DatePickerDialog.OnDateSetListener { _: DatePicker?, year: Int, monthOfYear: Int, dayOfMonth: Int ->
            date = Calendar.getInstance()
            date?.set(Calendar.YEAR, year)
            date?.set(Calendar.MONTH, monthOfYear)
            date?.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            if (date != null) {
                addTransactionBinding.tvSelectDate.text = date?.timeInMillis?.let { formatDateTZ(it) }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentFinanceBinding = FragmentFinanceBinding.inflate(inflater, container, false)
        fRealm = DatabaseService(requireActivity()).realmInstance
        date = Calendar.getInstance()
        fragmentFinanceBinding.btnFilter.setOnClickListener { showDatePickerDialog() }
        list = fRealm.where(RealmMyTeam::class.java).notEqualTo("status", "archived")
            .equalTo("teamId", teamId).equalTo("docType", "transaction")
            .sort("date", Sort.DESCENDING).findAllAsync()

        list?.addChangeListener { results ->
            updatedFinanceList(results)
        }

        fragmentFinanceBinding.llDate.setOnClickListener {
            fragmentFinanceBinding.imgDate.rotation += 180
            list = fRealm.where(RealmMyTeam::class.java).notEqualTo("status", "archived")
                .equalTo("teamId", teamId).equalTo("docType", "transaction")
                .sort("date", if (isAsc) Sort.DESCENDING else Sort.ASCENDING).findAll()
            updatedFinanceList(list as RealmResults<RealmMyTeam>)
            isAsc = !isAsc
        }
        fragmentFinanceBinding.btnReset.setOnClickListener {
            list = fRealm.where(RealmMyTeam::class.java).notEqualTo("status", "archived")
                .equalTo("teamId", teamId).equalTo("docType", "transaction")
                .sort("date", Sort.DESCENDING).findAll()
            updatedFinanceList(list as RealmResults<RealmMyTeam>)
        }
        return fragmentFinanceBinding.root
    }

    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()

        val dpd = DatePickerDialog.newInstance(
            { _: DatePickerDialog?, year: Int, monthOfYear: Int, dayOfMonth: Int, yearEnd: Int, monthOfYearEnd: Int, dayOfMonthEnd: Int ->
                val start = Calendar.getInstance()
                val end = Calendar.getInstance()
                start[year, monthOfYear] = dayOfMonth
                end[yearEnd, monthOfYearEnd] = dayOfMonthEnd
                val list = fRealm.where(RealmMyTeam::class.java)
                    .equalTo("teamId", teamId)
                    .equalTo("docType", "transaction")
                    .between("date", start.timeInMillis, end.timeInMillis)
                    .sort("date", Sort.DESCENDING)
                    .findAll()
                updatedFinanceList(list)
            },
            now[Calendar.YEAR],
            now[Calendar.MONTH],
            now[Calendar.DAY_OF_MONTH]
        )
        dpd.show(requireActivity().fragmentManager, "DATE_PICKER")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (user?.isManager() == true || user?.isLeader() == true) {
            fragmentFinanceBinding.addTransaction.visibility = View.VISIBLE
        } else {
            fragmentFinanceBinding.addTransaction.visibility = View.GONE
        }
        fragmentFinanceBinding.addTransaction.setOnClickListener { addTransaction() }
        list = fRealm.where(RealmMyTeam::class.java).notEqualTo("status", "archived")
            .equalTo("teamId", teamId).equalTo("docType", "transaction")
            .sort("date", Sort.DESCENDING).findAll()
        updatedFinanceList(list as RealmResults<RealmMyTeam>)
        showNoData(fragmentFinanceBinding.tvNodata, list?.size, "finances")
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun calculateTotal(list: List<RealmMyTeam>?) {
        var debit = 0
        var credit = 0
        for (team in list ?: emptyList()) {
            if ("credit".equals(team.type?.lowercase(Locale.getDefault()), ignoreCase = true)) {
                credit += team.amount
            } else {
                debit += team.amount
            }
        }
        val total = credit - debit
        fragmentFinanceBinding.tvDebit.text = getString(R.string.number_placeholder, debit)
        fragmentFinanceBinding.tvCredit.text = getString(R.string.number_placeholder, credit)
        fragmentFinanceBinding.tvBalance.text = getString(R.string.number_placeholder, total)
        if (total >= 0) fragmentFinanceBinding.balanceCaution.visibility = View.GONE
    }

    private fun addTransaction() {
        AlertDialog.Builder(requireActivity()).setView(setUpAlertUi()).setTitle(R.string.add_transaction)
            .setPositiveButton("Submit") { _: DialogInterface?, _: Int ->
                val type = addTransactionBinding.spnType.selectedItem.toString()
                val note = "${addTransactionBinding.tlNote.editText?.text}".trim { it <= ' ' }
                val amount = "${addTransactionBinding.tlAmount.editText?.text}".trim { it <= ' ' }
                if (note.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.note_is_required))
                } else if (amount.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.amount_is_required))
                } else if (date == null) {
                    Utilities.toast(activity, getString(R.string.date_is_required))
                } else {
                    fRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
                        createTransactionObject(realm, type, note, amount, date)
                    }, Realm.Transaction.OnSuccess {
                        Utilities.toast(activity, getString(R.string.transaction_added))
                        adapterFinance?.notifyDataSetChanged()
                        showNoData(fragmentFinanceBinding.tvNodata, adapterFinance?.itemCount, "finances")
                    })
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun createTransactionObject(realm: Realm, type: String?, note: String, amount: String, date: Calendar?) {
        val team = realm.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
        team.status = "active"
        if (date != null) {
            team.date = date.timeInMillis
        }
        if (type != null) {
            team.teamType = type
        }
        team.type = type
        team.description = note
        team.teamId = teamId
        team.amount = amount.toInt()
        team.parentCode = user?.parentCode
        team.teamPlanetCode = user?.planetCode
        team.teamType = "sync"
        team.docType = "transaction"
        team.updated = true
    }

    private fun setUpAlertUi(): View {
        addTransactionBinding = AddTransactionBinding.inflate(LayoutInflater.from(activity))
        addTransactionBinding.tvSelectDate.setOnClickListener {
            android.app.DatePickerDialog(requireActivity(), listener, date!![Calendar.YEAR], date!![Calendar.MONTH], date!![Calendar.DAY_OF_MONTH]).show()
        }
        return addTransactionBinding.root
    }

    private fun updatedFinanceList(results: RealmResults<RealmMyTeam>) {
        activity?.runOnUiThread {
            adapterFinance = AdapterFinance(requireActivity(), results)
            fragmentFinanceBinding.rvFinance.layoutManager = LinearLayoutManager(activity)
            fragmentFinanceBinding.rvFinance.adapter = adapterFinance
            adapterFinance?.notifyDataSetChanged()
            calculateTotal(results)
        }
    }
}
