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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
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
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class EnterprisesFinancesFragment : BaseTeamFragment() {
    private val viewModel: EnterprisesFinancesViewModel by viewModels()
    private var _binding: FragmentFinanceBinding? = null
    private val binding get() = _binding!!
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()).withZone(ZoneId.systemDefault())
    private lateinit var addTransactionBinding: DialogAddTransactionBinding
    private lateinit var financeAdapter: EnterprisesFinancesAdapter
    private lateinit var headerAdapter: FinanceHeaderAdapter
    private val headerState = HeaderState()
    private val PAYLOAD_HEADER = "PAYLOAD_HEADER"
    var date: Calendar? = null
    private var transactions: List<Transaction> = emptyList()
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
        return binding.root
    }

    private fun setupHeaderListeners(hBinding: HeaderFinanceBinding) {
        hBinding.tvFromDateCalendar.setOnClickListener {
            showDatePickerDialog(isFromDate = true)
        }
        hBinding.tvFromDateCalendarIcon.setOnClickListener {
            showDatePickerDialog(isFromDate = true)
        }
        hBinding.etToDate.setOnClickListener {
            if (headerState.fromDate.isNotEmpty()) {
                showDatePickerDialog(isFromDate = false)
            }
        }
        hBinding.tvToDateCalendarIcon.setOnClickListener {
            if (headerState.fromDate.isNotEmpty()) {
                showDatePickerDialog(isFromDate = false)
            }
        }
        hBinding.llDate.setOnClickListener {
            headerState.isAsc = !headerState.isAsc
            headerAdapter.notifyItemChanged(0, PAYLOAD_HEADER)
            observeTransactions(sortAscending = headerState.isAsc)
        }
        hBinding.btnReset.setOnClickListener {
            resetFilterAndSort()
            observeTransactions()
        }
    }

    private fun bindHeader(hBinding: HeaderFinanceBinding) {
        hBinding.tvFromDateCalendar.setText(headerState.fromDate)
        hBinding.etToDate.setText(headerState.toDate)
        hBinding.etToDate.isEnabled = headerState.isToDateEnabled
        hBinding.tvToDateCalendarIcon.isEnabled = headerState.isToDateEnabled
        hBinding.etToDate.alpha = if (headerState.isToDateEnabled) 1.0f else 0.5f
        hBinding.tvToDateCalendarIcon.alpha = if (headerState.isToDateEnabled) 1.0f else 0.5f

        hBinding.imgDate.rotation = if (headerState.isAsc) 180f else 0f

        val state = viewModel.headerState.value
        hBinding.tvDebit.text = getString(R.string.number_placeholder, state.debit)
        hBinding.tvCredit.text = getString(R.string.number_placeholder, state.credit)
        hBinding.tvBalance.text = getString(R.string.number_placeholder, state.total)
        hBinding.balanceCaution.visibility = if (state.isCautionVisible) View.VISIBLE else View.GONE
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
            val fromDateText = headerState.fromDate
            if (fromDateText.isNotEmpty()) parseDate(fromDateText) ?: now else now
        } else {
            val toDateText = headerState.toDate
            if (toDateText.isNotEmpty()) {
                parseDate(toDateText) ?: now
            } else {
                val fromDateText = headerState.fromDate
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
                    headerState.fromDate = formattedDate
                    val toDateText = headerState.toDate
                    if (toDateText.isNotEmpty()) {
                        val fromDateMillis = selectedDate.timeInMillis
                        val toDateMillis = parseDate(toDateText)?.timeInMillis
                        if (toDateMillis != null && toDateMillis < fromDateMillis) {
                            headerState.toDate = ""
                        }
                    }
                    headerState.isToDateEnabled = true
                } else {
                    headerState.toDate = formattedDate
                }

                headerAdapter.notifyItemChanged(0, PAYLOAD_HEADER)
                filterIfBothDatesSelected()
            },
            initialDate[Calendar.YEAR],
            initialDate[Calendar.MONTH],
            initialDate[Calendar.DAY_OF_MONTH]
        )

        datePickerDialog.datePicker.maxDate = maxDay.timeInMillis

        if (!isFromDate) {
            val fromDateText = headerState.fromDate
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
        val fromDate = headerState.fromDate
        val toDate = headerState.toDate
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
        headerAdapter = FinanceHeaderAdapter(
            onCreateHeader = { hBinding -> setupHeaderListeners(hBinding) },
            onBindHeader = { hBinding -> bindHeader(hBinding) }
        )
        binding.rvFinance.layoutManager = LinearLayoutManager(activity)
        (binding.rvFinance.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.rvFinance.adapter = ConcatAdapter(headerAdapter, financeAdapter)

        collectLatestWhenStarted(isMemberFlow) { isMember ->
            val canManage = if (fromCommunity) user?.isManager() == true else isMember
            binding.addTransaction.visibility = if (canManage) View.VISIBLE else View.GONE
        }
        collectLatestWhenStarted(viewModel.transactions) { results ->
            transactions = results
            updatedFinanceList(results)
        }
        collectLatestWhenStarted(viewModel.headerState) {
            headerAdapter.notifyItemChanged(0, PAYLOAD_HEADER)
        }
        collectWhenStarted(viewModel.transactionCreated) { result ->
            if (result.isSuccess) {
                Utilities.toast(activity, getString(R.string.transaction_added))
            } else {
                val errorMessage = result.exceptionOrNull()?.localizedMessage
                    ?: getString(R.string.no_data_available_please_check_and_try_again)
                Utilities.toast(activity, errorMessage)
            }
        }

        observeTransactions()
    }

    override fun onNewsItemClick(news: News?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
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
                    val capturedDate = date ?: return@setPositiveButton
                    viewModel.createTransaction(
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

        binding.rvFinance.visibility = View.VISIBLE
        binding.tvNodata.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun resetFilterAndSort() {
        headerState.fromDate = ""
        headerState.toDate = ""
        headerState.isToDateEnabled = false
        headerState.isAsc = false
        currentStartDate = null
        currentEndDate = null
        if (::headerAdapter.isInitialized) {
            headerAdapter.notifyItemChanged(0, PAYLOAD_HEADER)
        }
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
        _binding = null
        super.onDestroyView()
    }

    private fun observeTransactions(
        sortAscending: Boolean = headerState.isAsc,
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

    private data class HeaderState(
        var fromDate: String = "",
        var toDate: String = "",
        var isToDateEnabled: Boolean = false,
        var isAsc: Boolean = false,
    )

    private class FinanceHeaderAdapter(
        private val onCreateHeader: (HeaderFinanceBinding) -> Unit,
        private val onBindHeader: (HeaderFinanceBinding) -> Unit
    ) : RecyclerView.Adapter<FinanceHeaderAdapter.HeaderViewHolder>() {
        class HeaderViewHolder(val binding: HeaderFinanceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
            val binding = HeaderFinanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            onCreateHeader(binding)
            return HeaderViewHolder(binding)
        }

        override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
            onBindHeader(holder.binding)
        }

        override fun onBindViewHolder(holder: HeaderViewHolder, position: Int, payloads: MutableList<Any>) {
            onBindHeader(holder.binding)
        }

        override fun getItemCount(): Int = 1
    }
}
