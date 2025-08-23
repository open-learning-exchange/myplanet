package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.coroutineScope
import fisk.chipcloud.ChipCloud
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities

class NewsLabelManager(
    private val context: Context,
    private val newsRepository: NewsRepository,
    private val currentUser: RealmUserModel?
) {
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
                if (selectedLabel != null && news?.labels?.contains(selectedLabel) == false) {
                    binding.root.context as? androidx.lifecycle.LifecycleOwner
                    val lifecycleOwner = binding.root.context as androidx.lifecycle.LifecycleOwner
                    lifecycleOwner.lifecycle.coroutineScope.launch {
                        newsRepository.addLabelToNews(news.id, selectedLabel)
                        Utilities.toast(context, context.getString(R.string.label_added))
                        showChips(binding, news)
                    }
                    false
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
                    val lifecycleOwner = binding.root.context as androidx.lifecycle.LifecycleOwner
                    lifecycleOwner.lifecycle.coroutineScope.launch {
                        val labelToRemove = Constants.LABELS[labelText]
                        if (labelToRemove != null) {
                            newsRepository.removeLabelFromNews(news.id, labelToRemove)
                            showChips(binding, news)
                        }
                    }
                }
            }
        }
        updateAddLabelVisibility(binding, news)
    }

    private fun updateAddLabelVisibility(binding: RowNewsBinding, news: RealmNews?) {
        val usedLabels = news?.labels?.toSet() ?: emptySet()
        val labels = Constants.LABELS.values.toSet()
        if (usedLabels.containsAll(labels)) binding.btnAddLabel.visibility = View.GONE
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
