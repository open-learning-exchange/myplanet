package org.ole.planet.myplanet.ui.enterprises

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.databinding.DialogAddTransactionBinding
import org.ole.planet.myplanet.databinding.FragmentFinanceBinding
import org.ole.planet.myplanet.databinding.HeaderFinanceBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TimeUtils.formatDateTZ
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class EnterprisesFinancesFragment : BaseTeamFragment() {
    private val viewModel: EnterprisesFinancesViewModel by viewModels()
    private var _binding: FragmentFinanceBinding? = null
    private val binding get() = _binding!!
    private var _headerBinding: HeaderFinanceBinding? = null
    private val headerBinding get() = _headerBinding!!
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()).withZone(ZoneId.systemDefault())
    private lateinit var addTransactionBinding: DialogAddTransactionBinding
    private lateinit var financeAdapter: EnterprisesFinancesAdapter
    var date: Calendar? = null
    private var transactions: List<Transaction> = emptyList()
    private var isAsc = false
    private var currentStartDate: Long? = null
    private var currentEndDate: Long? = null
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private var selectedImageUri: Uri? = null
    private var dialogImagePreview: ImageView? = null
    private val fromCommunity: Boolean
        get() = arguments?.getBoolean("fromCommunity", false) == true

    var listener =
        DatePickerDialog.OnDateSetListener { _: DatePicker?, year: Int, monthOfYear: Int, dayOfMonth: Int ->
            date = Calendar.getInstance()
            date?.set(Calendar.YEAR, year)
            date?.set(Calendar.MONTH, monthOfYear)
            date?.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            if (date != null) {
                addTransactionBinding.tvSelectDate.text = date?.timeInMillis?.let { formatDateTZ(it) }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFinanceBinding.inflate(inflater, container, false)
        _headerBinding = HeaderFinanceBinding.inflate(inflater, binding.rvFinance, false)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                dialogImagePreview?.let { preview ->
                    preview.visibility = View.VISIBLE
                    Glide.with(this).load(uri).into(preview)
                }
            }
        }
        date = Calendar.getInstance()
        updateToDateState(false)
        headerBinding.tvFromDateCalendar.setOnClickListener {
            showDatePickerDialog(isFromDate = true)
        }

        headerBinding.tvFromDateCalendarIcon.setOnClickListener {
            showDatePickerDialog(isFromDate = true)
        }

        headerBinding.etToDate.setOnClickListener {
            if (headerBinding.tvFromDateCalendar.text.toString().isNotEmpty()) {
                showDatePickerDialog(isFromDate = false)
            }
        }

        headerBinding.tvToDateCalendarIcon.setOnClickListener {
            if (headerBinding.tvFromDateCalendar.text.toString().isNotEmpty()) {
                showDatePickerDialog(isFromDate = false)
            }
        }

        headerBinding.llDate.setOnClickListener {
            headerBinding.imgDate.rotation += 180
            val newSort = !isAsc
            isAsc = newSort
            observeTransactions(sortAscending = newSort)
        }
        headerBinding.btnReset.setOnClickListener {
            resetFilterAndSort()
            observeTransactions()
        }
        return binding.root
    }

    private fun showDatePickerDialog(isFromDate: Boolean) {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val maxDay = Calendar.getInstance()

        val initialDate = if (isFromDate) {
            val fromDateText = headerBinding.tvFromDateCalendar.text.toString()
            if (fromDateText.isNotEmpty()) parseDate(fromDateText) ?: now else now
        } else {
            val toDateText = headerBinding.etToDate.text.toString()
            if (toDateText.isNotEmpty()) {
                parseDate(toDateText) ?: now
            } else {
                val fromDateText = headerBinding.tvFromDateCalendar.text.toString()
                if (fromDateText.isNotEmpty()) parseDate(fromDateText) ?: now else now
            }
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, monthOfYear, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, monthOfYear, dayOfMonth)
                }
                val formattedDate = selectedDate.formatToString()

                if (isFromDate) {
                    headerBinding.tvFromDateCalendar.setText(formattedDate)
                    val toDateText = headerBinding.etToDate.text.toString()
                    if (toDateText.isNotEmpty()) {
                        val fromDateMillis = selectedDate.timeInMillis
                        val toDateMillis = parseDate(toDateText)?.timeInMillis
                        if (toDateMillis != null && toDateMillis < fromDateMillis) {
                            headerBinding.etToDate.setText("")
                        }
                    }
                    updateToDateState(true)
                } else {
                    headerBinding.etToDate.setText(formattedDate)
                }

                filterIfBothDatesSelected()
            },
            initialDate[Calendar.YEAR],
            initialDate[Calendar.MONTH],
            initialDate[Calendar.DAY_OF_MONTH]
        )

        datePickerDialog.datePicker.maxDate = maxDay.timeInMillis

        if (!isFromDate) {
            val fromDateText = headerBinding.tvFromDateCalendar.text.toString()
            if (fromDateText.isNotEmpty()) {
                val fromDate = parseDate(fromDateText)
                if (fromDate != null) {
                    datePickerDialog.datePicker.minDate = fromDate.timeInMillis
                }
            }
        }
        datePickerDialog.show()
    }

    private fun Calendar.formatToString(): String {
        return dateFormatter.format(this.toInstant())
    }

    private fun updateToDateState(enabled: Boolean) {
        headerBinding.etToDate.isEnabled = enabled
        headerBinding.tvToDateCalendarIcon.isEnabled = enabled
        headerBinding.etToDate.alpha = if (enabled) 1.0f else 0.5f
        headerBinding.tvToDateCalendarIcon.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun parseDate(dateString: String): Calendar? {
        return try {
            val localDate = LocalDate.parse(dateString, dateFormatter)
            Calendar.getInstance().apply {
                timeInMillis = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun filterIfBothDatesSelected() {
        val fromDate = headerBinding.tvFromDateCalendar.text.toString()
        val toDate = headerBinding.etToDate.text.toString()
        if (fromDate.isNotEmpty() && toDate.isNotEmpty()) {
            filterDataByDateRange(fromDate, toDate)
        }
    }

    private fun filterDataByDateRange(fromDate: String, toDate: String) {
        try {
            val start = LocalDate.parse(fromDate, dateFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = LocalDate.parse(toDate, dateFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            currentStartDate = start
            currentEndDate = end
            observeTransactions()

        } catch (e: DateTimeParseException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.addTransaction.setOnClickListener { addTransaction() }
        financeAdapter = EnterprisesFinancesAdapter(requireActivity())
        val headerAdapter = FinanceHeaderAdapter(headerBinding)
        binding.rvFinance.layoutManager = LinearLayoutManager(activity)
        binding.rvFinance.adapter = ConcatAdapter(headerAdapter, financeAdapter)

        collectLatestWhenStarted(isMemberFlow) { isMember ->
            val canManage = if (fromCommunity) user?.isManager() == true else isMember
            binding.addTransaction.visibility = if (canManage) View.VISIBLE else View.GONE
        }
        collectLatestWhenStarted(viewModel.transactions) { results ->
            transactions = results
            updatedFinanceList(results)
            showNoData(binding.tvNodata, transactions.size, "finances")
        }

        observeTransactions()
    }

    override fun onNewsItemClick(news: News?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun calculateTotal(list: List<Transaction>) {
        var debit = 0
        var credit = 0
        for (team in list) {
            if ("credit".equals(team.type?.lowercase(Locale.getDefault()), ignoreCase = true)) {
                credit += team.amount
            } else {
                debit += team.amount
            }
        }
        val total = credit - debit
        headerBinding.tvDebit.text = getString(R.string.number_placeholder, debit)
        headerBinding.tvCredit.text = getString(R.string.number_placeholder, credit)
        headerBinding.tvBalance.text = getString(R.string.number_placeholder, total)
        headerBinding.balanceCaution.visibility = if (total < 0) View.VISIBLE else View.GONE
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
                    val amountValue = amount.toIntOrNull()
                    if (amountValue == null) {
                        Utilities.toast(activity, getString(R.string.amount_is_required))
                        return@setPositiveButton
                    }
                    val imageUri = selectedImageUri
                    val imageName = imageUri?.let { FileUtils.getDisplayName(requireContext(), it, timeProvider) }
                    val imageData = imageUri?.let { FileUtils.readBytesFromUri(requireContext(), it) }
                    viewLifecycleOwner.lifecycleScope.launch {
                        val capturedDate = date ?: return@launch
                        val result = teamsRepository.createTransaction(
                            teamId = teamId,
                            type = type,
                            note = note,
                            amount = amountValue,
                            date = capturedDate.timeInMillis,
                            parentCode = user?.parentCode,
                            planetCode = user?.planetCode,
                            imageName = imageName,
                            imageData = imageData,
                        )
                        if (result.isSuccess) {
                            Utilities.toast(activity, getString(R.string.transaction_added))
                        } else {
                            val errorMessage = result.exceptionOrNull()?.localizedMessage
                                ?: getString(R.string.no_data_available_please_check_and_try_again)
                            Utilities.toast(activity, errorMessage)
                        }
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setUpAlertUi(): View {
        addTransactionBinding = DialogAddTransactionBinding.inflate(LayoutInflater.from(activity))
        selectedImageUri = null
        dialogImagePreview = addTransactionBinding.imagePreview
        addTransactionBinding.btnAddImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        addTransactionBinding.tvSelectDate.setOnClickListener {
            val d = date ?: Calendar.getInstance()
            DatePickerDialog(requireActivity(), listener, d[Calendar.YEAR], d[Calendar.MONTH], d[Calendar.DAY_OF_MONTH]).show()
        }
        return addTransactionBinding.root
    }

    private fun updatedFinanceList(results: List<Transaction>) {
        if (view == null) return

        financeAdapter.submitList(results)
        calculateTotal(results)

        if (results.isNotEmpty() || headerBinding.tvFromDateCalendar.text?.isNotEmpty() == true || headerBinding.etToDate.text?.isNotEmpty() == true) {
            binding.tvNodata.visibility = View.GONE
            binding.rvFinance.visibility = View.VISIBLE
        } else {
            binding.tvNodata.visibility = View.VISIBLE
            binding.rvFinance.visibility = View.GONE
        }
    }

    private fun resetFilterAndSort() {
        _headerBinding?.let { header ->
            header.tvFromDateCalendar.setText("")
            header.etToDate.setText("")
            updateToDateState(false)
            header.imgDate.rotation = 0f
        }
        currentStartDate = null
        currentEndDate = null
        isAsc = false
    }

    override fun onResume() {
        super.onResume()
        observeTransactions()
    }

    override fun onPause() {
        super.onPause()
        resetFilterAndSort()
    }

    override fun onDestroyView() {
        resetFilterAndSort()
        transactions = emptyList()
        _headerBinding = null
        _binding = null
        super.onDestroyView()
    }

    private fun observeTransactions(
        sortAscending: Boolean = isAsc,
        startDate: Long? = currentStartDate,
        endDate: Long? = currentEndDate,
    ) {
        viewModel.getTeamTransactions(
            teamId = teamId,
            sortAscending = sortAscending,
            startDate = startDate,
            endDate = endDate
        )
    }

    private class FinanceHeaderAdapter(
        private val headerBinding: HeaderFinanceBinding
    ) : RecyclerView.Adapter<FinanceHeaderAdapter.HeaderViewHolder>() {
        class HeaderViewHolder(val binding: HeaderFinanceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
            return HeaderViewHolder(headerBinding)
        }

        override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {}

        override fun getItemCount(): Int = 1
    }
}
