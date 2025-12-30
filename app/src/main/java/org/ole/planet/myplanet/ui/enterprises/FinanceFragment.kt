package org.ole.planet.myplanet.ui.enterprises

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AddTransactionBinding
import org.ole.planet.myplanet.databinding.FragmentFinanceBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.TimeUtils.formatDateTZ
import org.ole.planet.myplanet.utilities.Utilities

class FinanceFragment : BaseTeamFragment() {
    private var _binding: FragmentFinanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var addTransactionBinding: AddTransactionBinding
    private lateinit var financeAdapter: FinanceAdapter
    var date: Calendar? = null
    private var transactions: List<Transaction> = emptyList()
    private var isAsc = false
    private var transactionsJob: Job? = null
    private var currentStartDate: Long? = null
    private var currentEndDate: Long? = null

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
        _binding = FragmentFinanceBinding.inflate(inflater, container, false)
        date = Calendar.getInstance()
        updateToDateState(false)
        binding.tvFromDateCalendar.setOnClickListener {
            showDatePickerDialog(isFromDate = true)
        }

        binding.tvFromDateCalendarIcon.setOnClickListener {
            showDatePickerDialog(isFromDate = true)
        }

        binding.etToDate.setOnClickListener {
            if (binding.tvFromDateCalendar.text.toString().isNotEmpty()) {
                showDatePickerDialog(isFromDate = false)
            }
        }

        binding.tvToDateCalendarIcon.setOnClickListener {
            if (binding.tvFromDateCalendar.text.toString().isNotEmpty()) {
                showDatePickerDialog(isFromDate = false)
            }
        }


        binding.llDate.setOnClickListener {
            binding.imgDate.rotation += 180
            val newSort = !isAsc
            isAsc = newSort
            observeTransactions(sortAscending = newSort)
        }
        binding.btnReset.setOnClickListener {
            binding.tvFromDateCalendar.setText("")
            binding.etToDate.setText("")
            updateToDateState(false)
            currentStartDate = null
            currentEndDate = null
            isAsc = false
            binding.imgDate.rotation = 0f
            observeTransactions(sortAscending = isAsc, startDate = null, endDate = null)
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
        val datePickerDialog = android.app.DatePickerDialog(
            requireContext(),
            { _, year, monthOfYear, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, monthOfYear, dayOfMonth)
                }
                val formattedDate = selectedDate.formatToString("yyyy-MM-dd")

                if (isFromDate) {
                    binding.tvFromDateCalendar.setText(formattedDate)
                    val toDateText = binding.etToDate.text.toString()
                    if (toDateText.isNotEmpty()) {
                        val fromDateMillis = selectedDate.timeInMillis
                        val toDateMillis = parseDate(toDateText)?.timeInMillis
                        if (toDateMillis != null && toDateMillis < fromDateMillis) {
                            binding.etToDate.setText("")
                        }
                    }
                    updateToDateState(true)
                } else {
                    binding.etToDate.setText(formattedDate)
                }

                filterIfBothDatesSelected()
            },
            now[Calendar.YEAR],
            now[Calendar.MONTH],
            now[Calendar.DAY_OF_MONTH]
        )

        if (!isFromDate) {
            val fromDateText = binding.tvFromDateCalendar.text.toString()
            if (fromDateText.isNotEmpty()) {
                val fromDate = parseDate(fromDateText)
                if (fromDate != null) {
                    datePickerDialog.datePicker.minDate = fromDate.timeInMillis
                }
            }
        }
        datePickerDialog.show()
    }


    private fun Calendar.formatToString(pattern: String): String {
        val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        return dateFormat.format(this.time)
    }

    private fun updateToDateState(enabled: Boolean) {
        binding.etToDate.isEnabled = enabled
        binding.tvToDateCalendarIcon.isEnabled = enabled
        binding.etToDate.alpha = if (enabled) 1.0f else 0.5f
        binding.tvToDateCalendarIcon.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun parseDate(dateString: String): Calendar? {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.parse(dateString)
            if (date != null) {
                Calendar.getInstance().apply {
                    time = date
                }
            } else {
                null
            }
        } catch (e: ParseException) {
            null
        }
    }


    private fun filterIfBothDatesSelected() {
        val fromDate = binding.tvFromDateCalendar.text.toString()
        val toDate = binding.etToDate.text.toString()
        if (fromDate.isNotEmpty() && toDate.isNotEmpty()) {
            filterDataByDateRange(fromDate, toDate)
        }
    }


    private fun filterDataByDateRange(fromDate: String, toDate: String) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val start = dateFormat.parse(fromDate)?.time ?: throw IllegalArgumentException("Invalid fromDate format")
            val end = dateFormat.parse(toDate)?.time ?: throw IllegalArgumentException("Invalid toDate format")
            currentStartDate = start
            currentEndDate = end
            observeTransactions()

        } catch (e: ParseException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (user?.isManager() == true || user?.isLeader() == true) {
            binding.addTransaction.visibility = View.VISIBLE
        } else {
            binding.addTransaction.visibility = View.GONE
        }
        binding.addTransaction.setOnClickListener { addTransaction() }
        financeAdapter = FinanceAdapter(requireActivity())
        binding.rvFinance.layoutManager = LinearLayoutManager(activity)
        binding.rvFinance.adapter = financeAdapter
        observeTransactions()
    }

    override fun onNewsItemClick(news: RealmNews?) {}
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
        binding.tvDebit.text = getString(R.string.number_placeholder, debit)
        binding.tvCredit.text = getString(R.string.number_placeholder, credit)
        binding.tvBalance.text = getString(R.string.number_placeholder, total)
        if (total >= 0) binding.balanceCaution.visibility = View.GONE
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
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = teamsRepository.createTransaction(
                            teamId = teamId,
                            type = type,
                            note = note,
                            amount = amountValue,
                            date = date!!.timeInMillis,
                            parentCode = user?.parentCode,
                            planetCode = user?.planetCode,
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
        addTransactionBinding = AddTransactionBinding.inflate(LayoutInflater.from(activity))
        addTransactionBinding.tvSelectDate.setOnClickListener {
            android.app.DatePickerDialog(requireActivity(), listener, date!![Calendar.YEAR], date!![Calendar.MONTH], date!![Calendar.DAY_OF_MONTH]).show()
        }
        return addTransactionBinding.root
    }

    private fun updatedFinanceList(results: List<Transaction>) {
        if (view == null) return

        financeAdapter.submitList(results)
        calculateTotal(results)

        if (results.isNotEmpty()) {
            binding.dataLayout.visibility = View.VISIBLE
            binding.tvNodata.visibility = View.GONE
            binding.rvFinance.visibility = View.VISIBLE
        } else if (binding.tvFromDateCalendar.text.isNullOrEmpty() && binding.etToDate.text.isNullOrEmpty()) {
            binding.dataLayout.visibility = View.GONE
            binding.tvNodata.visibility = View.VISIBLE
            binding.rvFinance.visibility = View.GONE
        } else {
            binding.dataLayout.visibility = View.VISIBLE
            binding.tvNodata.visibility = View.VISIBLE
            binding.rvFinance.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        transactionsJob?.cancel()
        transactionsJob = null
        transactions = emptyList()
        _binding = null
        super.onDestroyView()
    }

    private fun observeTransactions(
        sortAscending: Boolean = isAsc,
        startDate: Long? = currentStartDate,
        endDate: Long? = currentEndDate,
    ) {
        transactionsJob?.cancel()
        transactionsJob = viewLifecycleOwner.lifecycleScope.launch {
            teamsRepository.getTeamTransactionsWithBalance(
                teamId = teamId,
                startDate = startDate,
                endDate = endDate,
                sortAscending = sortAscending,
            ).collectLatest { results ->
                transactions = results
                updatedFinanceList(results)
                showNoData(binding.tvNodata, transactions.size, "finances")
            }
        }
    }
}
