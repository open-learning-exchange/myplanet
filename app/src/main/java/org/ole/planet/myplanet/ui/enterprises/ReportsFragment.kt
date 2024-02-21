package org.ole.planet.myplanet.ui.enterprises

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.DialogAddReportBinding
import org.ole.planet.myplanet.databinding.FragmentReportsBinding
import java.util.Calendar

class ReportsFragment : Fragment() {
    private lateinit var fragmentReportsBinding: FragmentReportsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentReportsBinding = FragmentReportsBinding.inflate(inflater, container, false)
        fragmentReportsBinding.addReports.setOnClickListener{
            val dialogBuilder = AlertDialog.Builder(requireContext())
            val dialogBinding = DialogAddReportBinding.inflate(layoutInflater)
            dialogBuilder.setView(dialogBinding.root)
            dialogBinding.ltStartDate.setOnClickListener {
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val dpd = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                    dialogBinding.startDate.text = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                }, year, month, day)

                dpd.show()
            }

            dialogBinding.ltEndDate.setOnClickListener {
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val dpd = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                    dialogBinding.endDate.text = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                }, year, month, day)

                dpd.show()
            }

            dialogBuilder
                .setCancelable(false)
                .setPositiveButton("Submit") { _, _ ->
                    val startDate = "${dialogBinding.startDate.text}"
                    val endDate = "${dialogBinding.endDate.text}"
                    val summary = "${dialogBinding.summary.text}"
                    val beginningBalance = "${dialogBinding.beginningBalance.text}".toDoubleOrNull() ?: 0.0
                    val sales = "${dialogBinding.sales.text}".toDoubleOrNull() ?: 0.0
                    val otherIncome = "${dialogBinding.otherIncome.text}".toDoubleOrNull() ?: 0.0
                    val personnel = "${dialogBinding.personnel.text}".toDoubleOrNull() ?: 0.0
                    val nonPersonnel = "${dialogBinding.nonPersonnel.text}".toDoubleOrNull() ?: 0.0
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                }

            val dialog = dialogBuilder.create()
            dialog.show()
        }

        return fragmentReportsBinding.root
    }
}