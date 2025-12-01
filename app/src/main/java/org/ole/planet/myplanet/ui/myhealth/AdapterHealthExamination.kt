package org.ole.planet.myplanet.ui.myhealth

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertExaminationBinding
import org.ole.planet.myplanet.databinding.RowExaminationBinding
import org.ole.planet.myplanet.ui.myhealth.AdapterHealthExamination.ViewHolderMyHealthExamination
import java.util.Date

class AdapterHealthExamination(
    private val context: Context,
) : ListAdapter<HealthExaminationItem, ViewHolderMyHealthExamination>(HealthExaminationDiffCallback()) {

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
        binding.txtDate.tag = item.date
        if (item.isSelfExamination) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_green_50))
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_50))
        }

        binding.txtPulse.text = item.pulse
        binding.txtBp.text = item.bloodPressure
        binding.txtHearing.text = item.hearing
        binding.txtHeight.text = item.height
        binding.txtWeight.text = item.weight
        binding.txtVision.text = item.vision
        holder.itemView.setOnClickListener {
            showAlert(binding, position)
        }
    }

    private fun showAlert(binding: RowExaminationBinding, position: Int) {
        val item = getItem(position)
        val alertExaminationBinding = AlertExaminationBinding.inflate(LayoutInflater.from(context))
        alertExaminationBinding.tvVitals.text = item.vitals
        alertExaminationBinding.tvCondition.text = item.conditions
        alertExaminationBinding.tvOtherNotes.text = item.otherNotes
        val dialog = AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(binding.txtDate.tag as? CharSequence ?: binding.txtDate.text)
            .setView(alertExaminationBinding.root)
            .setPositiveButton("OK", null).create()
        val backgroundColor = ContextCompat.getColor(context, R.color.multi_select_grey)
        dialog.window?.setBackgroundDrawable(backgroundColor.toDrawable())
        val time = Date().time - 5000 * 60
        if (item.date >= time) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.edit)) { _: DialogInterface?, _: Int ->
                context.startActivity(
                    Intent(context, AddExaminationActivity::class.java)
                        .putExtra("id", item._id)
                        .putExtra("userId", item.userId)
                )
            }
        }
        dialog.show()
    }

    class ViewHolderMyHealthExamination(val binding: RowExaminationBinding) : RecyclerView.ViewHolder(binding.root)
}

class HealthExaminationDiffCallback : DiffUtil.ItemCallback<HealthExaminationItem>() {
    override fun areItemsTheSame(oldItem: HealthExaminationItem, newItem: HealthExaminationItem): Boolean {
        return oldItem._id == newItem._id
    }

    override fun areContentsTheSame(oldItem: HealthExaminationItem, newItem: HealthExaminationItem): Boolean {
        return oldItem == newItem
    }
}
