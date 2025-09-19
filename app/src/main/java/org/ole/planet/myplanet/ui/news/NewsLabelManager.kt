package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.view.MenuItem
import android.view.View
import fisk.chipcloud.ChipCloud
import io.realm.Realm
import io.realm.RealmList
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities
import java.util.concurrent.atomic.AtomicBoolean

class NewsLabelManager(private val context: Context, private val realm: Realm, private val currentUser: RealmUserModel?) {
    fun setupAddLabelMenu(binding: RowNewsBinding, news: RealmNews?) {
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
                            news?.let { showChips(binding, it) }
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

    fun showChips(binding: RowNewsBinding, news: RealmNews) {
        val isOwner = (news.userId == currentUser?.id)
        binding.fbChips.removeAllViews()

        for (label in news.labels ?: emptyList()) {
            val chipConfig = Utilities.getCloudConfig().apply {
                selectMode(if (isOwner) ChipCloud.SelectMode.close else ChipCloud.SelectMode.none)
            }

            val chipCloud = ChipCloud(context, binding.fbChips, chipConfig)
            chipCloud.addChip(getLabel(label))

            if (isOwner) {
                chipCloud.setDeleteListener { _: Int, labelText: String? ->
                    val selectedLabel = Constants.LABELS[labelText]
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
                                showChips(binding, news)
                            }
                        }, { error ->
                            error.printStackTrace()
                        })
                    }
                }
            }
        }
        updateAddLabelVisibility(binding, news)
    }

    private fun updateAddLabelVisibility(binding: RowNewsBinding, news: RealmNews?) {
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
        return ""
    }
}

