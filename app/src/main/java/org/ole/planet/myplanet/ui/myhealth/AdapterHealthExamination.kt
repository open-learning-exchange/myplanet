package org.ole.planet.myplanet.ui.myhealth

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import java.util.Date
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertExaminationBinding
import org.ole.planet.myplanet.databinding.RowExaminationBinding
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.myhealth.AdapterHealthExamination.ViewHolderMyHealthExamination
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities

class AdapterHealthExamination(
    private val context: Context,
    private var mh: RealmMyHealthPojo,
    private var userModel: RealmUserModel?,
    private var userMap: Map<String, RealmUserModel>
) : ListAdapter<RealmMyHealthPojo, ViewHolderMyHealthExamination>(diffCallback) {
    private val displayNameCache = mutableMapOf<String, String>()

    fun updateData(mh: RealmMyHealthPojo, userModel: RealmUserModel?, userMap: Map<String, RealmUserModel>) {
        this.mh = mh
        this.userModel = userModel
        this.userMap = userMap
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyHealthExamination {
        val rowExaminationBinding = RowExaminationBinding.inflate(
            LayoutInflater.from(context), parent, false
        )
        return ViewHolderMyHealthExamination(rowExaminationBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMyHealthExamination, position: Int) {
        val binding = holder.binding
        val item = getItem(position)
        binding.txtTemp.text = item.let { checkEmpty(it.temperature) }
        val formattedDate = item.let { formatDate(it.date, "MMM dd, yyyy") }
        binding.txtDate.text = formattedDate
        binding.txtDate.tag = formattedDate
        val encrypted = userModel?.let { it1 -> item.getEncryptedDataAsJson(it1) }

        val createdBy = getString("createdBy", encrypted)
        if (!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)) {
            val name = displayNameCache.getOrPut(createdBy) {
                val model = userMap[createdBy]
                model?.getFullName() ?: createdBy.split(colonRegex).dropLastWhile { it.isEmpty() }.toTypedArray().getOrNull(1) ?: createdBy
            }
            binding.txtDate.text = context.getString(R.string.two_strings, binding.txtDate.text, name).trimIndent()
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_50))
        } else {
            binding.txtDate.text = context.getString(R.string.self_examination, binding.txtDate.text)
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_green_50))
        }

        binding.txtPulse.text = item.let { checkEmptyInt(it.pulse) }
        binding.txtBp.text = item.bp
        binding.txtHearing.text = item.hearing
        binding.txtHeight.text = item.let { checkEmpty(it.height) }
        binding.txtWeight.text = item.let { checkEmpty(it.weight) }
        binding.txtVision.text = item.vision
        holder.itemView.setOnClickListener {
            if (encrypted != null) {
                showAlert(binding, position, encrypted)
            }
        }
    }

    private fun checkEmpty(value: Float): String {
        return if (value == 0f) "" else value.toString() + ""
    }

    private fun checkEmptyInt(value: Int): String {
        return if (value == 0) "" else value.toString() + ""
    }

    private fun showAlert(binding: RowExaminationBinding, position: Int, encrypted: JsonObject) {
        val realmExamination = getItem(position)
        val alertExaminationBinding = AlertExaminationBinding.inflate(LayoutInflater.from(context))
        if (realmExamination != null) {
            alertExaminationBinding.tvVitals.text = context.getString(R.string.vitals_format, checkEmpty(realmExamination.temperature),
                checkEmptyInt(realmExamination.pulse), realmExamination.bp, checkEmpty(realmExamination.height),
                checkEmpty(realmExamination.weight), realmExamination.vision, realmExamination.hearing).trimIndent()
        }
        showConditions(alertExaminationBinding.tvCondition, realmExamination)
        showEncryptedData(alertExaminationBinding.tvOtherNotes, encrypted)
        val dialog = AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(binding.txtDate.tag as? CharSequence ?: binding.txtDate.text)
            .setView(alertExaminationBinding.root)
            .setPositiveButton("OK", null).create()
        val backgroundColor = ContextCompat.getColor(context, R.color.multi_select_grey)
        dialog.window?.setBackgroundDrawable(backgroundColor.toDrawable())
        val time = Date().time - 5000 * 60
        if (realmExamination != null) {
            if (realmExamination.date >= time) { dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.edit)) { _: DialogInterface?, _: Int ->
                context.startActivity(Intent(context, AddExaminationActivity::class.java)
                    .putExtra("id", getItem(position)._id)
                    .putExtra("userId", mh._id))
            }
            }
        }
        dialog.show()
    }

    private fun showConditions(tvCondition: TextView, realmExamination: RealmMyHealthPojo?) {
        val conditionsMap = JsonUtils.gson.fromJson(realmExamination?.conditions, JsonObject::class.java)
        val keys = conditionsMap.keySet()
        val conditions = StringBuilder()
        for (key in keys) {
            if (conditionsMap[key].asBoolean) {
                conditions.append("$key, ")
            }
        }
        tvCondition.text = conditions
    }

    private fun showEncryptedData(tvOtherNotes: TextView, encrypted: JsonObject) {
        tvOtherNotes.text = context.getString(R.string.observations_notes_colon, Utilities.checkNA(getString("notes", encrypted)),
            Utilities.checkNA(getString("diagnosis", encrypted)), Utilities.checkNA(getString("treatments", encrypted)),
            Utilities.checkNA(getString("medications", encrypted)), Utilities.checkNA(getString("immunizations", encrypted)),
            Utilities.checkNA(getString("allergies", encrypted)), Utilities.checkNA(getString("xrays", encrypted)),
            Utilities.checkNA(getString("tests", encrypted)), Utilities.checkNA(getString("referrals", encrypted)))
    }

    class ViewHolderMyHealthExamination(val binding: RowExaminationBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val colonRegex by lazy { ":".toRegex() }
        private val diffCallback = DiffUtils.itemCallback<RealmMyHealthPojo>(
            { oldItem, newItem -> oldItem._id == newItem._id },
            { oldItem, newItem -> oldItem == newItem }
        )
    }
}
