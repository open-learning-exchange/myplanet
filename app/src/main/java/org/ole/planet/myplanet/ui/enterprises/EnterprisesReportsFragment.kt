package org.ole.planet.myplanet.ui.enterprises

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.databinding.DialogAddReportBinding
import org.ole.planet.myplanet.databinding.FragmentReportsBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class EnterprisesReportsFragment : BaseTeamFragment() {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private var reports: List<RealmMyTeam> = emptyList()
    private lateinit var reportsAdapter: EnterprisesReportsAdapter
    private var startTimeStamp: String? = null
    private var endTimeStamp: String? = null
    lateinit var teamType: String
    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>
    private val viewModel: EnterprisesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        binding.addReports.isVisible = false
        binding.addReports.setOnClickListener{
            showAddReportDialog()
        }

        binding.exportCSV.setOnClickListener {
            val currentDate = Date()
            val dateFormat = SimpleDateFormat("EEE_MMM_dd_yyyy", Locale.US)
            val formattedDate = dateFormat.format(currentDate)
            val teamName = prefData.getTeamName()?.replace(" ", "_")

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "Report_of_${teamName}_Financial_Report_Summary_on_${formattedDate}")
            }
            createFileLauncher.launch(intent)
        }

        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val csvContent = viewModel.exportReportsAsCsv(reports, prefData.getTeamName() ?: "")
                            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(csvContent.toByteArray())
                            }
                            Utilities.toast(requireContext(), getString(R.string.csv_file_saved_successfully))
                        } catch (e: IOException) {
                            e.printStackTrace()
                            Utilities.toast(requireContext(), getString(R.string.failed_to_save_csv_file))
                        }
                    }
                } ?: Utilities.toast(requireContext(), getString(R.string.export_cancelled))
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        reportsAdapter = EnterprisesReportsAdapter(
            requireContext(),
            prefData,
            onEdit = { report -> showEditReportDialog(report) },
            onDelete = { report -> showDeleteReportDialog(report) }
        )
        binding.rvReports.adapter = reportsAdapter
        binding.rvReports.layoutManager = LinearLayoutManager(activity)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    isMemberFlow.collectLatest { isMember ->
                        binding.addReports.isVisible = isMember
                        reportsAdapter.setNonTeamMember(!isMember)
                    }
                }
                launch {
                    viewModel.getReportsFlow(teamId).collectLatest { reportList ->
                        updatedReportsList(reportList)
                    }
                }
            }
        }
    }

    private fun showAddReportDialog() {
        val dialogAddReportBinding = DialogAddReportBinding.inflate(LayoutInflater.from(requireContext()))
        val v: View = dialogAddReportBinding.root
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
        builder.setTitle(R.string.add_report)
            .setView(v)
            .setPositiveButton("submit", null)
            .setNegativeButton("cancel", null)
        val dialog = builder.create()
        dialog.show()
        val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfMonth = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}"
        startTimeStamp = "${calendar.timeInMillis}"

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val lastDayOfMonth = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}"
        endTimeStamp = "${calendar.timeInMillis}"

        dialogAddReportBinding.startDate.text = firstDayOfMonth
        dialogAddReportBinding.endDate.text = lastDayOfMonth

        setupDatePickers(dialogAddReportBinding, calendar, true)

        submit.setOnClickListener {
            if (isValidReportForm(dialogAddReportBinding)) {
                val doc = JsonObject().apply {
                    addProperty("_id", UUID.randomUUID().toString())
                    addProperty("createdDate", System.currentTimeMillis())
                    addProperty("description", "${dialogAddReportBinding.summary.text}")
                    addProperty("beginningBalance", dialogAddReportBinding.beginningBalance.text.toString().toIntOrNull() ?: 0)
                    addProperty("sales", dialogAddReportBinding.sales.text.toString().toIntOrNull() ?: 0)
                    addProperty("otherIncome", dialogAddReportBinding.otherIncome.text.toString().toIntOrNull() ?: 0)
                    addProperty("wages", dialogAddReportBinding.personnel.text.toString().toIntOrNull() ?: 0)
                    addProperty("otherExpenses", dialogAddReportBinding.nonPersonnel.text.toString().toIntOrNull() ?: 0)
                    addProperty("startDate", startTimeStamp?.toLongOrNull() ?: 0L)
                    addProperty("endDate", endTimeStamp?.toLongOrNull() ?: 0L)
                    addProperty("updatedDate", System.currentTimeMillis())
                    addProperty("teamId", teamId)
                    addProperty("teamType", team?.teamType)
                    addProperty("teamPlanetCode", team?.teamPlanetCode)
                    addProperty("docType", "report")
                    addProperty("updated", true)
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.addReport(doc)
                        dialog.dismiss()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Utilities.toast(requireContext(), "Failed to add report. Please try again.")
                    }
                }
            }
        }

        cancel.setOnClickListener { dialog.dismiss() }
    }

    private fun showEditReportDialog(currentReport: RealmMyTeam) {
        val dialogAddReportBinding = DialogAddReportBinding.inflate(LayoutInflater.from(requireContext()))
        val v: View = dialogAddReportBinding.root
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
        builder.setTitle("Edit Report")
            .setView(v)
            .setPositiveButton("submit", null)
            .setNegativeButton("cancel", null)
        val dialog = builder.create()
        dialog.show()
        val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        startTimeStamp = currentReport.startDate.toString()
        endTimeStamp = currentReport.endDate.toString()

        dialogAddReportBinding.startDate.text = getString(R.string.message_placeholder, TimeUtils.formatDate(currentReport.startDate, " MMM dd, yyyy"))
        dialogAddReportBinding.endDate.text = getString(R.string.message_placeholder, TimeUtils.formatDate(currentReport.endDate, " MMM dd, yyyy"))
        dialogAddReportBinding.summary.setText(getString(R.string.message_placeholder, currentReport.description))
        dialogAddReportBinding.beginningBalance.setText(getString(R.string.number_placeholder, currentReport.beginningBalance))
        dialogAddReportBinding.sales.setText(getString(R.string.number_placeholder, currentReport.sales))
        dialogAddReportBinding.otherIncome.setText(getString(R.string.number_placeholder, currentReport.otherIncome))
        dialogAddReportBinding.personnel.setText(getString(R.string.number_placeholder, currentReport.wages))
        dialogAddReportBinding.nonPersonnel.setText(getString(R.string.number_placeholder, currentReport.otherExpenses))

        setupDatePickers(dialogAddReportBinding, calendar, false)

        submit.setOnClickListener {
            if (isValidReportForm(dialogAddReportBinding)) {
                val reportId = currentReport._id
                if (reportId.isNullOrBlank()) {
                    Snackbar.make(
                        binding.root,
                        "Failed to update report. Please try again.",
                        Snackbar.LENGTH_LONG,
                    ).show()
                    return@setOnClickListener
                }
                val doc = JsonObject().apply {
                    addProperty("description", dialogAddReportBinding.summary.text.toString())
                    addProperty(
                        "beginningBalance",
                        dialogAddReportBinding.beginningBalance.text.toString().toIntOrNull()
                             ?: currentReport.beginningBalance,
                    )
                    addProperty(
                        "sales",
                        dialogAddReportBinding.sales.text.toString().toIntOrNull()
                             ?: currentReport.sales,
                    )
                    addProperty(
                        "otherIncome",
                        dialogAddReportBinding.otherIncome.text.toString().toIntOrNull()
                             ?: currentReport.otherIncome,
                    )
                    addProperty(
                        "wages",
                        dialogAddReportBinding.personnel.text.toString().toIntOrNull()
                             ?: currentReport.wages,
                    )
                    addProperty(
                        "otherExpenses",
                        dialogAddReportBinding.nonPersonnel.text.toString().toIntOrNull()
                             ?: currentReport.otherExpenses,
                    )
                    addProperty("startDate", startTimeStamp?.toLongOrNull() ?: currentReport.startDate)
                    addProperty("endDate", endTimeStamp?.toLongOrNull() ?: currentReport.endDate)
                    addProperty("updatedDate", System.currentTimeMillis())
                    addProperty("updated", true)
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.updateReport(reportId, doc)
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Snackbar.make(
                            binding.root,
                            "Failed to update report. Please try again.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        cancel.setOnClickListener { dialog.dismiss() }
    }

    private fun showDeleteReportDialog(report: RealmMyTeam) {
        report._id?.let { reportId ->
            val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            builder.setTitle(getString(R.string.delete_report))
                .setMessage(R.string.delete_record)
                .setPositiveButton(R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            viewModel.archiveReport(reportId)
                        } catch (e: Exception) {
                            binding.root.let { view ->
                                Snackbar.make(view, getString(R.string.failed_to_delete_report), Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupDatePickers(dialogAddReportBinding: DialogAddReportBinding, calendar: Calendar, isAdd: Boolean) {
        dialogAddReportBinding.ltStartDate.setOnClickListener {
            showDatePickerDialog(calendar) { timeInMillis, formattedDate ->
                dialogAddReportBinding.startDate.text = formattedDate
                startTimeStamp = if (isAdd) "$timeInMillis" else getString(R.string.number_placeholder, timeInMillis)
            }
        }

        dialogAddReportBinding.ltEndDate.setOnClickListener {
            showDatePickerDialog(calendar) { timeInMillis, formattedDate ->
                dialogAddReportBinding.endDate.text = formattedDate
                endTimeStamp = "$timeInMillis"
            }
        }
    }

    private fun showDatePickerDialog(calendar: Calendar, onDateSelected: (Long, String) -> Unit) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(Calendar.YEAR, selectedYear)
            calendar.set(Calendar.MONTH, selectedMonth)
            calendar.set(Calendar.DAY_OF_MONTH, selectedDay)
            val formattedDate = getString(R.string.formatted_date, selectedDay, selectedMonth + 1, selectedYear)
            onDateSelected(calendar.timeInMillis, formattedDate)
        }, year, month, day).show()
    }

    private fun isValidReportForm(binding: DialogAddReportBinding): Boolean {
        return when {
            binding.startDate.text == "Start Date" -> {
                binding.startDate.error = "start date is required"
                false
            }
            binding.endDate.text == "End Date" -> {
                binding.endDate.error = "end date is required"
                false
            }
            TextUtils.isEmpty("${binding.summary.text}") -> {
                binding.summary.error = "summary is required"
                false
            }
            TextUtils.isEmpty("${binding.beginningBalance.text}") -> {
                binding.beginningBalance.error = "beginning balance is required"
                false
            }
            TextUtils.isEmpty("${binding.sales.text}") -> {
                binding.sales.error = "sales is required"
                false
            }
            TextUtils.isEmpty("${binding.otherIncome.text}") -> {
                binding.otherIncome.error = "other income is required"
                false
            }
            TextUtils.isEmpty("${binding.personnel.text}") -> {
                binding.personnel.error = "personnel is required"
                false
            }
            TextUtils.isEmpty("${binding.nonPersonnel.text}") -> {
                binding.nonPersonnel.error = "non-personnel is required"
                false
            }
            else -> true
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun updatedReportsList(results: List<RealmMyTeam>) {
        if (_binding == null) return
        reports = results
        reportsAdapter.submitList(reports)
        BaseRecyclerFragment.showNoData(binding.tvMessage, reports.size, "reports")
        binding.exportCSV.visibility = if (reports.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
