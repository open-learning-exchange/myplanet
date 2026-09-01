package org.ole.planet.myplanet.ui.health

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
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertExaminationBinding
import org.ole.planet.myplanet.databinding.RowExaminationBinding
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.ui.health.HealthExaminationAdapter.HealthExaminationViewHolder
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.TimeUtils.formatDate
import org.ole.planet.myplanet.utils.Utilities

class HealthExaminationAdapter(
    private val context: Context,
    private var mh: HealthExamination,
    private var userModel: UserEntity?,
    private var userMap: Map<String, UserEntity>,
    private val dispatcherProvider: DispatcherProvider
) : ListAdapter<HealthExaminationAdapter.HealthExaminationItem, HealthExaminationViewHolder>(DIFF_CALLBACK) {

    data class HealthExaminationItem(
        val examination: HealthExamination,
        val formattedDate: String,
        val isSelfExamination: Boolean,
        val resolvedName: String,
        val hasEncryptedData: Boolean
    )

    private val colorGrey50 by lazy { ContextCompat.getColor(context, R.color.md_grey_50) }
    private val colorGreen50 by lazy { ContextCompat.getColor(context, R.color.md_green_50) }
    private val colorMultiSelectGrey by lazy { ContextCompat.getColor(context, R.color.multi_select_grey) }

    suspend fun updateData(mh: HealthExamination, userModel: UserEntity?, userMap: Map<String, UserEntity>, newItems: List<HealthExamination>? = null) {
        this.mh = mh
        this.userModel = userModel
        this.userMap = userMap
        val listToProcess = newItems ?: currentList.map { it.examination }
        submitExaminations(listToProcess)
    }

    suspend fun submitExaminations(list: List<HealthExamination>) {
        val items = withContext(dispatcherProvider.default) {
            val displayNameCache = mutableMapOf<String, String>()
            list.map { item ->
                val formattedDate = formatDate(item.date, "MMM dd, yyyy")
                val encrypted = userModel?.let { user -> item.getEncryptedDataAsJson(user) }
                val createdBy = getString("createdBy", encrypted)

                val (resolvedName, isSelfExamination) = if (!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)) {
                    val name = displayNameCache.getOrPut(createdBy) {
                        val model = userMap[createdBy]
                        model?.getFullName() ?: createdBy.split(colonRegex).dropLastWhile { it.isEmpty() }.toTypedArray().getOrNull(1) ?: createdBy
                    }
                    name to false
                } else {
                    "" to true
                }

                HealthExaminationItem(
                    examination = item,
                    formattedDate = formattedDate,
                    isSelfExamination = isSelfExamination,
                    resolvedName = resolvedName,
                    hasEncryptedData = encrypted != null
                )
            }
        }
        withContext(dispatcherProvider.main) {
            submitList(items)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HealthExaminationViewHolder {
        val rowExaminationBinding = RowExaminationBinding.inflate(
            LayoutInflater.from(context), parent, false
        )
        return HealthExaminationViewHolder(rowExaminationBinding)
    }

    override fun onBindViewHolder(holder: HealthExaminationViewHolder, position: Int) {
        val binding = holder.binding
        val item = getItem(position)
        val realmExamination = item.examination

        binding.txtTemp.text = checkEmpty(realmExamination.temperature)
        if (!item.isSelfExamination) {
            binding.txtDate.text = context.getString(R.string.two_strings, item.formattedDate, item.resolvedName).trimIndent()
            holder.itemView.setBackgroundColor(colorGrey50)
        } else {
            binding.txtDate.text = context.getString(R.string.self_examination, item.formattedDate)
            holder.itemView.setBackgroundColor(colorGreen50)
        }
        binding.txtDate.tag = item.formattedDate

        binding.txtPulse.text = checkEmptyInt(realmExamination.pulse)
        binding.txtBp.text = realmExamination.bp
        binding.txtHearing.text = realmExamination.hearing
        binding.txtHeight.text = checkEmpty(realmExamination.height)
        binding.txtWeight.text = checkEmpty(realmExamination.weight)
        binding.txtVision.text = realmExamination.vision

        holder.itemView.setOnClickListener {
            if (item.hasEncryptedData) {
                val encrypted = userModel?.let { user -> item.examination.getEncryptedDataAsJson(user) } ?: JsonObject()
                showAlert(binding, item, encrypted)
            }
        }
    }

    private fun checkEmpty(value: Float): String {
        return if (value == 0f) "" else value.toString() + ""
    }

    private fun checkEmptyInt(value: Int): String {
        return if (value == 0) "" else value.toString() + ""
    }

    private fun showAlert(binding: RowExaminationBinding, item: HealthExaminationItem, encrypted: JsonObject) {
        val realmExamination = item.examination
        val alertExaminationBinding = AlertExaminationBinding.inflate(LayoutInflater.from(context))

        alertExaminationBinding.tvVitals.text = context.getString(R.string.vitals_format, checkEmpty(realmExamination.temperature),
            checkEmptyInt(realmExamination.pulse), realmExamination.bp, checkEmpty(realmExamination.height),
            checkEmpty(realmExamination.weight), realmExamination.vision, realmExamination.hearing).trimIndent()

        alertExaminationBinding.tvCondition.text = HealthExamination.formatConditions(realmExamination.conditions)
        showEncryptedData(alertExaminationBinding.tvOtherNotes, encrypted)

        val dialog = AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(binding.txtDate.tag as? CharSequence ?: binding.txtDate.text)
            .setView(alertExaminationBinding.root)
            .setPositiveButton("OK", null).create()
        dialog.window?.setBackgroundDrawable(colorMultiSelectGrey.toDrawable())

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.edit)) { _: DialogInterface?, _: Int ->
            context.startActivity(Intent(context, HealthExaminationActivity::class.java)
                .putExtra("id", realmExamination._id)
                .putExtra("userId", mh._id))
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

    class HealthExaminationViewHolder(val binding: RowExaminationBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val colonRegex by lazy { ":".toRegex() }
        private val DIFF_CALLBACK = DiffUtils.itemCallback<HealthExaminationItem>(
            { oldItem, newItem -> oldItem.examination._id == newItem.examination._id },
            { oldItem, newItem ->
                oldItem.formattedDate == newItem.formattedDate &&
                oldItem.resolvedName == newItem.resolvedName &&
                oldItem.isSelfExamination == newItem.isSelfExamination &&
                oldItem.examination.temperature == newItem.examination.temperature &&
                oldItem.examination.date == newItem.examination.date &&
                oldItem.examination.data == newItem.examination.data &&
                oldItem.examination.pulse == newItem.examination.pulse &&
                oldItem.examination.bp == newItem.examination.bp &&
                oldItem.examination.hearing == newItem.examination.hearing &&
                oldItem.examination.height == newItem.examination.height &&
                oldItem.examination.weight == newItem.examination.weight &&
                oldItem.examination.vision == newItem.examination.vision
            }
        )
    }
}
