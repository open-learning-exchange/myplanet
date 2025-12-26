package org.ole.planet.myplanet.ui.voices

import android.content.Context
import android.view.MenuItem
import android.view.View
import fisk.chipcloud.ChipCloud
import io.realm.Realm
import io.realm.RealmList
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowVoicesBinding
import org.ole.planet.myplanet.model.RealmVoices
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities

class VoicesLabelManager(private val context: Context, private val realm: Realm) {
    fun setupAddLabelMenu(binding: RowVoicesBinding, voices: RealmVoices?, canManageLabels: Boolean) {
        binding.btnAddLabel.setOnClickListener(null)
        binding.btnAddLabel.isEnabled = canManageLabels
        if (!canManageLabels) {
            return
        }

        binding.btnAddLabel.setOnClickListener {
            val usedLabels = voices?.labels?.toSet() ?: emptySet()
            val availableLabels = Constants.LABELS.filterValues { it !in usedLabels }

            val wrapper = androidx.appcompat.view.ContextThemeWrapper(context, R.style.CustomPopupMenu)
            val menu = android.widget.PopupMenu(wrapper, binding.btnAddLabel)
            availableLabels.keys.forEach { labelName ->
                menu.menu.add(labelName)
            }
            menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                val selectedLabel = Constants.LABELS[menuItem.title]
                val voicesId = voices?.id
                if (selectedLabel != null && voicesId != null) {
                    if (voices?.labels?.contains(selectedLabel) == true) {
                        return@setOnMenuItemClickListener true
                    }

                    val labelAdded = AtomicBoolean(false)
                    realm.executeTransactionAsync({ transactionRealm ->
                        val managedVoices = transactionRealm.where(RealmVoices::class.java)
                            .equalTo("id", voicesId)
                            .findFirst()
                        if (managedVoices != null) {
                            var managedLabels = managedVoices.labels
                            if (managedLabels == null) {
                                managedLabels = RealmList()
                                managedVoices.labels = managedLabels
                            }
                            if (!managedLabels.contains(selectedLabel)) {
                                managedLabels.add(selectedLabel)
                                labelAdded.set(true)
                            }
                        }
                    }, {
                        if (labelAdded.get()) {
                            val managedVoices = realm.where(RealmVoices::class.java)
                                .equalTo("id", voicesId)
                                .findFirst()
                            val managedLabels = managedVoices?.labels
                            val newLabels = RealmList<String>().apply {
                                managedLabels?.forEach { add(it) }
                            }
                            voices?.labels = newLabels
                            Utilities.toast(context, context.getString(R.string.label_added))
                            voices?.let { showChips(binding, it, canManageLabels) }
                        }
                    }, { error ->
                        error.printStackTrace()
                    })
                    return@setOnMenuItemClickListener false
                }
                true
            }
            menu.show()
        }
    }

    fun showChips(binding: RowVoicesBinding, voices: RealmVoices, canManageLabels: Boolean) {
        binding.fbChips.removeAllViews()

        for (label in voices.labels ?: emptyList()) {
            val chipConfig = Utilities.getCloudConfig().apply {
                selectMode(if (canManageLabels) ChipCloud.SelectMode.close else ChipCloud.SelectMode.none)
            }

            val chipCloud = ChipCloud(context, binding.fbChips, chipConfig)
            chipCloud.addChip(getLabel(label))

            if (canManageLabels) {
                chipCloud.setDeleteListener { _: Int, labelText: String? ->
                    val selectedLabel = when {
                        labelText == null -> null
                        Constants.LABELS.containsKey(labelText) -> Constants.LABELS[labelText]
                        else -> voices.labels?.firstOrNull { getLabel(it) == labelText }
                    }
                    val voicesId = voices.id
                    if (selectedLabel != null && voicesId != null) {
                        val labelRemoved = AtomicBoolean(false)
                        realm.executeTransactionAsync({ transactionRealm ->
                            val managedVoices = transactionRealm.where(RealmVoices::class.java)
                                .equalTo("id", voicesId)
                                .findFirst()
                            if (managedVoices != null) {
                                var managedLabels = managedVoices.labels
                                if (managedLabels == null) {
                                    managedLabels = RealmList()
                                    managedVoices.labels = managedLabels
                                }
                                if (managedLabels.remove(selectedLabel)) {
                                    labelRemoved.set(true)
                                }
                            }
                        }, {
                            if (labelRemoved.get()) {
                                val managedVoices = realm.where(RealmVoices::class.java)
                                    .equalTo("id", voicesId)
                                    .findFirst()
                                val managedLabels = managedVoices?.labels
                                val newLabels = RealmList<String>().apply {
                                    managedLabels?.forEach { add(it) }
                                }
                                voices.labels = newLabels
                                showChips(binding, voices, canManageLabels)
                            }
                        }, { error ->
                            error.printStackTrace()
                        })
                    }
                }
            }
        }
        updateAddLabelVisibility(binding, voices, canManageLabels)
    }

    private fun updateAddLabelVisibility(
        binding: RowVoicesBinding,
        voices: RealmVoices?,
        canManageLabels: Boolean,
    ) {
        if (!canManageLabels) {
            binding.btnAddLabel.visibility = View.GONE
            return
        }

        val usedLabels = voices?.labels?.toSet() ?: emptySet()
        val labels = Constants.LABELS.values.toSet()
        binding.btnAddLabel.visibility =
            if (usedLabels.containsAll(labels)) View.GONE else View.VISIBLE
    }

    private fun getLabel(s: String): String {
        for (key in Constants.LABELS.keys) {
            if (s == Constants.LABELS[key]) {
                return key
            }
        }
        return formatLabelValue(s)
    }

    companion object {
        internal fun formatLabelValue(raw: String): String {
            val cleaned = raw.replace("_", " ").replace("-", " ")
            if (cleaned.isBlank()) {
                return raw
            }
            return cleaned
                .trim()
                .split(whitespaceRegex)
                .joinToString(" ") { part ->
                    part.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                    }
                }
        }
        private val whitespaceRegex by lazy { Regex("\\s+") }
    }
}
