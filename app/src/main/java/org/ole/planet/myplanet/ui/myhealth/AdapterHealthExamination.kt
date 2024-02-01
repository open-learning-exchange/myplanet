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
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertExaminationBinding
import org.ole.planet.myplanet.databinding.RowExaminationBinding
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.myhealth.AdapterHealthExamination.ViewHolderMyHealthExamination
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date

class AdapterHealthExamination(private val context: Context, private val list: List<RealmMyHealthPojo>, private val mh: RealmMyHealthPojo, private val userModel: RealmUserModel) : RecyclerView.Adapter<ViewHolderMyHealthExamination>() {
    private lateinit var rowExaminationBinding: RowExaminationBinding
    private lateinit var mRealm: Realm
    fun setmRealm(mRealm: Realm?) {
        if (mRealm != null) {
            this.mRealm = mRealm
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyHealthExamination {
        rowExaminationBinding = RowExaminationBinding.inflate(
            LayoutInflater.from(context), parent, false
        )
        return ViewHolderMyHealthExamination(rowExaminationBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMyHealthExamination, position: Int) {
        rowExaminationBinding.txtTemp.text = checkEmpty(list[position].temperature)
        rowExaminationBinding.txtDate.text = formatDate(list[position].date, "MMM dd, yyyy")
        val encrypted = list[position].getEncryptedDataAsJson(userModel)
        val createdBy = getString("createdBy", encrypted)
        if (!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel.id)) {
            val model = mRealm.where(RealmUserModel::class.java).equalTo("id", createdBy).findFirst()
            val name: String = model?.getFullName() ?: createdBy.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            rowExaminationBinding.txtDate.text = "${rowExaminationBinding.txtDate.text} $name".trimIndent()
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_50))

        } else {
            rowExaminationBinding.txtDate.text = rowExaminationBinding.txtDate.text.toString() + context.getString(R.string.self_examination)
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_green_50))
        }
        rowExaminationBinding.txtPulse.text = checkEmptyInt(list[position].pulse)
        rowExaminationBinding.txtBp.text = list[position].bp
        rowExaminationBinding.txtHearing.text = list[position].hearing
        rowExaminationBinding.txtHearing.text = checkEmpty(list[position].height)
        rowExaminationBinding.txtWeight.text = checkEmpty(list[position].weight)
        rowExaminationBinding.txtVision.text = list[position].vision
        holder.itemView.setOnClickListener { showAlert(position, encrypted) }
    }

    private fun checkEmpty(value: Float): String {
        return if (value == 0f) "" else value.toString() + ""
    }

    private fun checkEmptyInt(value: Int): String {
        return if (value == 0) "" else value.toString() + ""
    }

    private fun showAlert(position: Int, encrypted: JsonObject) {
        val realmExamination = list[position]
        val alertExaminationBinding = AlertExaminationBinding.inflate(LayoutInflater.from(context))
        alertExaminationBinding.tvVitals.text = """
            ${context.getString(R.string.temperature_colon)}${checkEmpty(realmExamination.temperature)}
            ${context.getString(R.string.pulse_colon)}${checkEmptyInt(realmExamination.pulse)}
            ${context.getString(R.string.blood_pressure_colon)}${realmExamination.bp}
            ${context.getString(R.string.height_colon)}${checkEmpty(realmExamination.height)}
            ${context.getString(R.string.weight_colon)}${checkEmpty(realmExamination.weight)}
            ${context.getString(R.string.vision_colon)}${realmExamination.vision}
            ${context.getString(R.string.hearing_colon)}${realmExamination.hearing}
            
            """.trimIndent()
        showConditions(alertExaminationBinding.tvCondition, realmExamination)
        showEncryptedData(alertExaminationBinding.tvOtherNotes, encrypted)
        val dialog = AlertDialog.Builder(context)
            .setTitle(formatDate(realmExamination.date, "MMM dd, yyyy"))
            .setView(alertExaminationBinding.root)
            .setPositiveButton("OK", null).create()
        val time = Date().time - 5000 * 60
        if (realmExamination.date >= time) { dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.edit)) {
            _: DialogInterface?, _: Int ->
            context.startActivity(Intent(context, AddExaminationActivity::class.java)
                .putExtra("id", list[position].get_id())
                .putExtra("userId", mh.get_id()))
            }
        }
        dialog.show()
    }

    private fun showConditions(tvCondition: TextView, realmExamination: RealmMyHealthPojo) {
        val conditionsMap = Gson().fromJson(realmExamination.conditions, JsonObject::class.java)
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
        tvOtherNotes.text = R.string.observations_notes_colon.toString() + Utilities.checkNA(
                getString("notes", encrypted)
            ) + "\n" + R.string.diagnosis_colon + Utilities.checkNA(
                getString("diagnosis", encrypted)
            ) + "\n" + R.string.treatments_colon + Utilities.checkNA(
                getString("treatments", encrypted)
            ) + "\n" + R.string.medications_colon + Utilities.checkNA(
                getString("medications", encrypted)
            ) + "\n" + R.string.immunizations_colon + Utilities.checkNA(
                getString("immunizations", encrypted)
            ) + "\n" + R.string.allergies_colon + Utilities.checkNA(
                getString("allergies", encrypted)
            ) + "\n" + R.string.x_rays_colon + Utilities.checkNA(
                getString("xrays", encrypted)
            ) + "\n" + R.string.lab_tests_colon + Utilities.checkNA(
                getString("tests", encrypted)
            ) + "\n" + R.string.referrals_colon + Utilities.checkNA(
                getString("referrals", encrypted)
            ) + "\n"
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderMyHealthExamination(rowExaminationBinding: RowExaminationBinding) : RecyclerView.ViewHolder(rowExaminationBinding.root)
}
