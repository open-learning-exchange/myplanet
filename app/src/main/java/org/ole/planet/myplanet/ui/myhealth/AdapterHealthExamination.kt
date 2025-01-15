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

class AdapterHealthExamination(private val context: Context, private val list: List<RealmMyHealthPojo>?, private val mh: RealmMyHealthPojo, private val userModel: RealmUserModel?) : RecyclerView.Adapter<ViewHolderMyHealthExamination>() {
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
            rowExaminationBinding.txtTemp.text = list?.get(position)?.temperature.toString()
            rowExaminationBinding.txtTemp.text = list?.get(position)?.let { checkEmpty(it.temperature) }
            rowExaminationBinding.txtDate.text = list?.get(position)?.let { formatDate(it.date, "MMM dd, yyyy") }
            val encrypted = userModel?.let { it1 -> list?.get(position)?.getEncryptedDataAsJson(it1) }


        val createdBy = getString("createdBy", encrypted)
        if (!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)) {
            val model = mRealm.where(RealmUserModel::class.java).equalTo("id", createdBy).findFirst()
            val name: String = model?.getFullName() ?: createdBy.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            rowExaminationBinding.txtDate.text = context.getString(R.string.two_strings, rowExaminationBinding.txtDate.text, name).trimIndent()
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_50))
        } else {
            rowExaminationBinding.txtDate.text = context.getString(R.string.self_examination, rowExaminationBinding.txtDate.text)
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_green_50))
        }

        rowExaminationBinding.txtPulse.text = list?.get(position)?.let { checkEmptyInt(it.pulse) }
        rowExaminationBinding.txtBp.text = list?.get(position)?.bp
        rowExaminationBinding.txtHearing.text = list?.get(position)?.hearing
        rowExaminationBinding.txtHeight.text = list?.get(position)?.let { checkEmpty(it.height) }
        rowExaminationBinding.txtWeight.text = list?.get(position)?.let { checkEmpty(it.weight) }
        rowExaminationBinding.txtVision.text = list?.get(position)?.vision
        holder.itemView.setOnClickListener {
            if (encrypted != null) {
                showAlert(position, encrypted)
            }
        }
    }

    private fun checkEmpty(value: Float): String {
        return if (value == 0f) "" else value.toString() + ""
    }

    private fun checkEmptyInt(value: Int): String {
        return if (value == 0) "" else value.toString() + ""
    }

    private fun showAlert(position: Int, encrypted: JsonObject) {
        val realmExamination = list?.get(position)
        val alertExaminationBinding = AlertExaminationBinding.inflate(LayoutInflater.from(context))
        if (realmExamination != null) {
            alertExaminationBinding.tvVitals.text = context.getString(R.string.vitals_format, checkEmpty(realmExamination.temperature),
                checkEmptyInt(realmExamination.pulse), realmExamination.bp, checkEmpty(realmExamination.height),
                checkEmpty(realmExamination.weight), realmExamination.vision, realmExamination.hearing).trimIndent()
        }
        showConditions(alertExaminationBinding.tvCondition, realmExamination)
        showEncryptedData(alertExaminationBinding.tvOtherNotes, encrypted)
        val dialog = AlertDialog.Builder(context)
            .setTitle(realmExamination?.let { formatDate(it.date, "MMM dd, yyyy") })
            .setView(alertExaminationBinding.root)
            .setPositiveButton("OK", null).create()
        val time = Date().time - 5000 * 60
        if (realmExamination != null) {
            if (realmExamination.date >= time) { dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.edit)) { _: DialogInterface?, _: Int ->
                context.startActivity(Intent(context, AddExaminationActivity::class.java)
                    .putExtra("id", list[position]._id)
                    .putExtra("userId", mh._id))
            }
            }
        }
        dialog.show()
    }

    private fun showConditions(tvCondition: TextView, realmExamination: RealmMyHealthPojo?) {
        val conditionsMap = Gson().fromJson(realmExamination?.conditions, JsonObject::class.java)
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

    override fun getItemCount(): Int {
        return list?.size ?: 0
    }

    class ViewHolderMyHealthExamination(rowExaminationBinding: RowExaminationBinding) : RecyclerView.ViewHolder(rowExaminationBinding.root)
}
