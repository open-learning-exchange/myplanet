package org.ole.planet.myplanet.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentSyncProgressBinding
import org.ole.planet.myplanet.databinding.ItemSyncTableProgressBinding
import org.ole.planet.myplanet.repository.SyncProgressRepository
import org.ole.planet.myplanet.repository.SyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SyncProgressBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var syncProgressRepository: SyncProgressRepository

    private var _binding: FragmentSyncProgressBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            syncProgressRepository.syncProgress.collectLatest { progress ->
                updateUI(progress)
            }
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun updateUI(progress: org.ole.planet.myplanet.repository.SyncProgress) {
        binding.llProgressContainer.removeAllViews()

        progress.perTableStatus.forEach { (table, status) ->
            val itemBinding = ItemSyncTableProgressBinding.inflate(layoutInflater, binding.llProgressContainer, false)
            itemBinding.tvTableName.text = table.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

            if (status.max > 0) {
                itemBinding.progressBar.isIndeterminate = false
                itemBinding.progressBar.max = status.max
                itemBinding.progressBar.progress = status.progress
                val percent = (status.progress * 100) / status.max
                itemBinding.tvProgressText.text = "$percent% (${status.progress}/${status.max})"
            } else {
                itemBinding.progressBar.isIndeterminate = true
                itemBinding.tvProgressText.text = "Syncing..."
            }

            if (status.status == SyncState.SUCCESS) {
                itemBinding.progressBar.progress = itemBinding.progressBar.max
                itemBinding.tvProgressText.text = "Completed"
                itemBinding.ivStatus.visibility = View.VISIBLE
                itemBinding.ivStatus.setImageResource(org.ole.planet.myplanet.R.drawable.ic_check_green)
            } else if (status.status == SyncState.FAILED) {
                 itemBinding.tvProgressText.text = "Failed"
                 itemBinding.ivStatus.visibility = View.VISIBLE
                 itemBinding.ivStatus.setImageResource(org.ole.planet.myplanet.R.drawable.ic_close_red)
            } else {
                 itemBinding.ivStatus.visibility = View.GONE
            }

            if (status.lastSyncTime > 0) {
                 val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                 itemBinding.tvLastSync.text = "Last sync: ${dateFormat.format(Date(status.lastSyncTime))}"
                 itemBinding.tvLastSync.visibility = View.VISIBLE
            } else {
                 itemBinding.tvLastSync.visibility = View.GONE
            }

            binding.llProgressContainer.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SyncProgressBottomSheet"
    }
}
