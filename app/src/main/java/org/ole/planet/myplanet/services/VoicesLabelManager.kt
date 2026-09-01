package org.ole.planet.myplanet.services

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.chip.Chip
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
            val chipContext = ContextThemeWrapper(context, R.style.Theme_App_Chip)
            for (label in labels) {
                val chip = Chip(chipContext).apply {
                    text = getLabel(label)
                    isCloseIconVisible = canManageLabels
                    if (canManageLabels) {
                        setOnCloseIconClickListener {
                            val selectedLabel = Constants.LABELS[label] ?: labels.firstOrNull { getLabel(it) == text }
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
                binding.fbChips.addView(chip)
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
        private val separatorRegex by lazy { Regex("[_-]") }
        private val whitespaceRegex by lazy { Regex("\\s+") }

        internal fun formatLabelValue(raw: String): String {
            val cleaned = raw.replace(separatorRegex, " ")
            if (cleaned.isBlank()) {
                return raw
            }
            val locale = Locale.getDefault()
            return cleaned
                .trim()
                .split(whitespaceRegex)
                .joinToString(" ") { part ->
                    part.lowercase(locale).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
                    }
                }
        }
    }
}
