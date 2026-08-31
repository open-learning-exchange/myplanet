package org.ole.planet.myplanet.services

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.view.ContextThemeWrapper
import fisk.chipcloud.ChipCloud
import java.util.Locale
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.Utilities

class VoicesLabelManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val addLabelFn: suspend (String, String) -> Unit,
    private val removeLabelFn: suspend (String, String) -> Unit
) {
    private val renderedStateCache = WeakHashMap<RowNewsBinding, RenderedState>()

    fun setupAddLabelMenu(binding: RowNewsBinding, voice: News?, canManageLabels: Boolean) {
        binding.btnAddLabel.setOnClickListener(null)
        binding.btnAddLabel.isEnabled = canManageLabels
        if (!canManageLabels) {
            return
        }

        binding.btnAddLabel.setOnClickListener {
            val usedLabels = voice?.labels?.toSet() ?: emptySet()
            val availableLabels = Constants.LABELS.filterValues { it !in usedLabels }

            val wrapper = ContextThemeWrapper(context, R.style.CustomPopupMenu)
            val menu = PopupMenu(wrapper, binding.btnAddLabel)
            availableLabels.keys.forEach { labelName ->
                menu.menu.add(labelName)
            }
            menu.setOnMenuItemClickListener { menuItem ->
                val selectedLabel = Constants.LABELS[menuItem.title]
                val voiceId = voice?.id
                if (selectedLabel != null && voiceId != null && voice.labels?.contains(selectedLabel) != true) {
                    scope.launch {
                        try {
                            addLabelFn(voiceId, selectedLabel)
                            withContext(dispatcherProvider.main) {
                                Utilities.toast(context, context.getString(R.string.label_added))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                true
            }
            menu.show()
        }
    }

    fun showChips(binding: RowNewsBinding, voice: News, canManageLabels: Boolean) {
        val labels = voice.labels ?: emptyList()

        val renderedState = RenderedState(voice.id, labels, canManageLabels)
        if (renderedStateCache[binding] == renderedState) {
            return
        }

        binding.fbChips.removeAllViews()

        if (labels.isNotEmpty()) {
            val chipConfig = Utilities.getCloudConfig().apply {
                selectMode(if (canManageLabels) ChipCloud.SelectMode.close else ChipCloud.SelectMode.none)
            }
            val chipCloud = ChipCloud(context, binding.fbChips, chipConfig)

            for (label in labels) {
                chipCloud.addChip(getLabel(label))
            }

            if (canManageLabels) {
                chipCloud.setDeleteListener { _: Int, labelText: String? ->
                    val selectedLabel = when {
                        labelText == null -> null
                        else -> Constants.LABELS[labelText] ?: labels.firstOrNull { getLabel(it) == labelText }
                    }
                    val voiceId = voice.id
                    if (selectedLabel != null && voiceId != null) {
                        scope.launch {
                            try {
                                removeLabelFn(voiceId, selectedLabel)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        renderedStateCache[binding] = renderedState
        updateAddLabelVisibility(binding, voice, canManageLabels)
    }

    private data class RenderedState(val voiceId: String?, val labels: List<String>, val canManageLabels: Boolean)

    private fun updateAddLabelVisibility(
        binding: RowNewsBinding,
        voice: News?,
        canManageLabels: Boolean,
    ) {
        if (!canManageLabels) {
            binding.btnAddLabel.visibility = View.GONE
            return
        }

        val usedLabels = voice?.labels?.toSet() ?: emptySet()
        val labels = Constants.LABELS.values.toSet()
        binding.btnAddLabel.visibility =
            if (usedLabels.containsAll(labels)) View.GONE else View.VISIBLE
    }

    private fun getLabel(s: String): String {
        return reverseLabels[s] ?: formatLabelValue(s)
    }

    companion object {
        private val reverseLabels by lazy { Constants.LABELS.entries.associate { it.value to it.key } }

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
