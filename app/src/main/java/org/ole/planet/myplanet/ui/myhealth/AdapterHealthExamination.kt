package org.ole.planet.myplanet.ui.myhealth

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import java.util.Date
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertExaminationBinding
import org.ole.planet.myplanet.databinding.RowExaminationBinding
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.Utilities

class AdapterHealthExamination(private val context: Context) : ListAdapter<HealthExaminationDisplayModel, AdapterHealthExamination.ViewHolderMyHealthExamination>(HealthExaminationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyHealthExamination {
        val rowExaminationBinding = RowExaminationBinding.inflate(
            LayoutInflater.from(context), parent, false
        )
        return ViewHolderMyHealthExamination(rowExaminationBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMyHealthExamination, position: Int) {
        val binding = holder.binding
        val item = getItem(position)
        binding.txtTemp.text = item.temperature
        binding.txtDate.text = item.displayDate
        binding.txtDate.tag = item.displayDate

        holder.itemView.setBackgroundColor(item.rowColor)

        binding.txtPulse.text = item.pulse
        binding.txtBp.text = item.bp
        binding.txtHearing.text = item.hearing
        binding.txtHeight.text = item.height
        binding.txtWeight.text = item.weight
        binding.txtVision.text = item.vision
        holder.itemView.setOnClickListener {
            if (item.encryptedData != null) {
                showAlert(binding, item)
            }
        }
    }

    private fun showAlert(binding: RowExaminationBinding, item: HealthExaminationDisplayModel) {
        val alertExaminationBinding = AlertExaminationBinding.inflate(LayoutInflater.from(context))
        alertExaminationBinding.tvVitals.text = context.getString(R.string.vitals_format, item.temperature,
            item.pulse, item.bp, item.height,
            item.weight, item.vision, item.hearing).trimIndent()

        alertExaminationBinding.tvCondition.text = item.conditionsDisplay
        showEncryptedData(alertExaminationBinding.tvOtherNotes, item.encryptedData!!)
        val dialog = AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(binding.txtDate.tag as? CharSequence ?: binding.txtDate.text)
            .setView(alertExaminationBinding.root)
            .setPositiveButton("OK", null).create()
        val backgroundColor = ContextCompat.getColor(context, R.color.multi_select_grey)
        dialog.window?.setBackgroundDrawable(backgroundColor.toDrawable())
        val time = Date().time - 5000 * 60
        if (item.dateLong >= time) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.edit)) { _: DialogInterface?, _: Int ->
                context.startActivity(Intent(context, AddExaminationActivity::class.java)
                    .putExtra("id", item.id)
                    .putExtra("userId", item.profileId))
            }
        }
        dialog.show()
    }

    private fun showEncryptedData(tvOtherNotes: TextView, encrypted: JsonObject) {
        tvOtherNotes.text = context.getString(R.string.observations_notes_colon, Utilities.checkNA(getString("notes", encrypted)),
            Utilities.checkNA(getString("diagnosis", encrypted)), Utilities.checkNA(getString("treatments", encrypted)),
            Utilities.checkNA(getString("medications", encrypted)), Utilities.checkNA(getString("immunizations", encrypted)),
            Utilities.checkNA(getString("allergies", encrypted)), Utilities.checkNA(getString("xrays", encrypted)),
            Utilities.checkNA(getString("tests", encrypted)), Utilities.checkNA(getString("referrals", encrypted)))
    }

    class ViewHolderMyHealthExamination(val binding: RowExaminationBinding) : RecyclerView.ViewHolder(binding.root)
}

class HealthExaminationDiffCallback : DiffUtil.ItemCallback<HealthExaminationDisplayModel>() {
    override fun areItemsTheSame(oldItem: HealthExaminationDisplayModel, newItem: HealthExaminationDisplayModel): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: HealthExaminationDisplayModel, newItem: HealthExaminationDisplayModel): Boolean {
        return oldItem == newItem
    }
}
