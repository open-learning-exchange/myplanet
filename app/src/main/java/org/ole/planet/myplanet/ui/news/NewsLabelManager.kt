package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.view.MenuItem
import android.view.View
import fisk.chipcloud.ChipCloud
import io.realm.Realm
import io.realm.RealmList
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities

class NewsLabelManager(private val context: Context, private val realm: Realm) {
    fun setupAddLabelMenu(binding: RowNewsBinding, news: RealmNews?, canManageLabels: Boolean) {
        binding.btnAddLabel.setOnClickListener(null)
        binding.btnAddLabel.isEnabled = canManageLabels
        if (!canManageLabels) {
            return
        }

        binding.btnAddLabel.setOnClickListener {
            val usedLabels = news?.labels?.toSet() ?: emptySet()
            val availableLabels = Constants.LABELS.filterValues { it !in usedLabels }

            val wrapper = androidx.appcompat.view.ContextThemeWrapper(context, R.style.CustomPopupMenu)
            val menu = android.widget.PopupMenu(wrapper, binding.btnAddLabel)
            availableLabels.keys.forEach { labelName ->
                menu.menu.add(labelName)
            }
            menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                val selectedLabel = Constants.LABELS[menuItem.title]
                val newsId = news?.id
                if (selectedLabel != null && newsId != null) {
                    if (news?.labels?.contains(selectedLabel) == true) {
                        return@setOnMenuItemClickListener true
                    }

                    val labelAdded = AtomicBoolean(false)
                    realm.executeTransactionAsync({ transactionRealm ->
                        val managedNews = transactionRealm.where(RealmNews::class.java)
                            .equalTo("id", newsId)
                            .findFirst()
                        if (managedNews != null) {
                            var managedLabels = managedNews.labels
                            if (managedLabels == null) {
                                managedLabels = RealmList()
                                managedNews.labels = managedLabels
                            }
                            if (!managedLabels.contains(selectedLabel)) {
                                managedLabels.add(selectedLabel)
                                labelAdded.set(true)
                            }
                        }
                    }, {
                        if (labelAdded.get()) {
                            val managedNews = realm.where(RealmNews::class.java)
                                .equalTo("id", newsId)
                                .findFirst()
                            val managedLabels = managedNews?.labels
                            val newLabels = RealmList<String>().apply {
                                managedLabels?.forEach { add(it) }
                            }
                            news?.labels = newLabels
                            Utilities.toast(context, context.getString(R.string.label_added))
                            news?.let { showChips(binding, it, canManageLabels) }
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

    fun showChips(binding: RowNewsBinding, news: RealmNews, canManageLabels: Boolean) {
        binding.fbChips.removeAllViews()

        for (label in news.labels ?: emptyList()) {
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
                        else -> news.labels?.firstOrNull { getLabel(it) == labelText }
                    }
                    val newsId = news.id
                    if (selectedLabel != null && newsId != null) {
                        val labelRemoved = AtomicBoolean(false)
                        realm.executeTransactionAsync({ transactionRealm ->
                            val managedNews = transactionRealm.where(RealmNews::class.java)
                                .equalTo("id", newsId)
                                .findFirst()
                            if (managedNews != null) {
                                var managedLabels = managedNews.labels
                                if (managedLabels == null) {
                                    managedLabels = RealmList()
                                    managedNews.labels = managedLabels
                                }
                                if (managedLabels.remove(selectedLabel)) {
                                    labelRemoved.set(true)
                                }
                            }
                        }, {
                            if (labelRemoved.get()) {
                                val managedNews = realm.where(RealmNews::class.java)
                                    .equalTo("id", newsId)
                                    .findFirst()
                                val managedLabels = managedNews?.labels
                                val newLabels = RealmList<String>().apply {
                                    managedLabels?.forEach { add(it) }
                                }
                                news.labels = newLabels
                                showChips(binding, news, canManageLabels)
                            }
                        }, { error ->
                            error.printStackTrace()
                        })
                    }
                }
            }
        }
        updateAddLabelVisibility(binding, news, canManageLabels)
    }

    private fun updateAddLabelVisibility(
        binding: RowNewsBinding,
        news: RealmNews?,
        canManageLabels: Boolean,
    ) {
        if (!canManageLabels) {
            binding.btnAddLabel.visibility = View.GONE
            return
        }

        val usedLabels = news?.labels?.toSet() ?: emptySet()
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
                .split(Regex("\\s+"))
                .joinToString(" ") { part ->
                    part.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                    }
                }
        }
    }
}

