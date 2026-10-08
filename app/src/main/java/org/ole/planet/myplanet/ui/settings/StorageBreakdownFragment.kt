package org.ole.planet.myplanet.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancel
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingBottomSheetFragment
import org.ole.planet.myplanet.databinding.FragmentStorageBreakdownBinding
import org.ole.planet.myplanet.databinding.ItemStorageCategoryBinding
import org.ole.planet.myplanet.services.FreeSpaceWorker
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DialogUtils.confirmDialog
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class StorageBreakdownFragment : BaseBindingBottomSheetFragment<FragmentStorageBreakdownBinding>(FragmentStorageBreakdownBinding::inflate) {

    private val viewModel: StorageBreakdownViewModel by viewModels()

    private var progressDialog: DialogUtils.CustomProgressDialog? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Refresh when returning from the detail screen after a deletion
        parentFragmentManager.setFragmentResultListener(
            StorageCategoryDetailFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            viewModel.loadStorage(forceRefresh = true)
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
        }

        binding.freeUpSpaceButton.setOnClickListener {
            requireContext().confirmDialog(
                title = getString(R.string.are_you_sure),
                message = getString(R.string.are_you_sure_want_to_delete_all_the_files),
                onPositive = ::freeUpSpace
            )
        }

        collectWhenStarted(viewModel.uiState) { state ->
            renderUiState(state)
        }
    }

    private fun freeUpSpace() {
        binding.freeUpSpaceButton.isEnabled = false

        val progressDialog = DialogUtils.getCustomProgressDialog(requireActivity())
        this.progressDialog = progressDialog
        progressDialog.show()

        val workManager = WorkManager.getInstance(requireContext())
        val freeSpaceWork = OneTimeWorkRequestBuilder<FreeSpaceWorker>()
            .addTag("freeSpaceWork")
            .build()
        workManager.enqueue(freeSpaceWork)

        collectWhenStarted(workManager.getWorkInfoByIdFlow(freeSpaceWork.id)) { workInfo ->
            if (workInfo != null) {
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress
                        val deletedFiles = progress.getInt("deletedFiles", 0)
                        val freedBytes = progress.getLong("freedBytes", 0)
                        progressDialog.setText(
                            getString(
                                R.string.storage_deleting_progress,
                                deletedFiles,
                                FileUtils.formatSize(requireContext(), freedBytes)
                            )
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        progressDialog.dismiss()
                        this@StorageBreakdownFragment.progressDialog = null
                        binding.freeUpSpaceButton.isEnabled = true
                        val output = workInfo.outputData
                        val deletedFiles = output.getInt("deletedFiles", 0)
                        val freedBytes = output.getLong("freedBytes", 0)
                        Utilities.toast(
                            requireActivity(),
                            getString(
                                R.string.storage_freed_summary,
                                FileUtils.formatSize(requireContext(), freedBytes),
                                deletedFiles
                            )
                        )
                        viewModel.loadStorage(forceRefresh = true)
                        parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
                    }
                    WorkInfo.State.FAILED -> {
                        progressDialog.dismiss()
                        this@StorageBreakdownFragment.progressDialog = null
                        binding.freeUpSpaceButton.isEnabled = true
                        Utilities.toast(requireActivity(), getString(R.string.unable_to_clear_files))
                        viewModel.loadStorage(forceRefresh = true)
                        parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
                    }
                    WorkInfo.State.CANCELLED -> {
                        progressDialog.dismiss()
                        this@StorageBreakdownFragment.progressDialog = null
                        binding.freeUpSpaceButton.isEnabled = true
                        viewModel.loadStorage(forceRefresh = true)
                        parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
                    }
                    else -> {
                        // ENQUEUED or BLOCKED
                    }
                }
                if (workInfo.state.isFinished) {
                    kotlinx.coroutines.currentCoroutineContext().cancel()
                }
            }
        }

        progressDialog.setNegativeButton(getString(R.string.cancel)) {
            workManager.cancelWorkById(freeSpaceWork.id)
        }
    }

    private fun renderUiState(state: StorageBreakdownUiState) {
        binding.availableSpaceText.text = getString(R.string.available_space_colon) +
            " " + state.availableSpaceText

        if (state.isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.contentLayout.visibility = View.GONE
            binding.emptyText.visibility = View.GONE
            return
        }

        binding.progressBar.visibility = View.GONE

        if (state.totalBytes == 0L) {
            binding.emptyText.visibility = View.VISIBLE
            binding.contentLayout.visibility = View.GONE
            return
        }

        binding.emptyText.visibility = View.GONE
        binding.totalSizeText.text = getString(R.string.storage_total_downloaded) + ": " +
            FileUtils.formatSize(requireContext(), state.totalBytes)
        binding.contentLayout.visibility = View.VISIBLE
        populateCategoryRows(state.categories)
    }

    private fun populateCategoryRows(categories: List<StorageBreakdownViewModel.CategoryData>) {
        binding.categoryContainer.removeAllViews()

        categories.filter { it.fileCount > 0 }.forEach { category ->
            val itemBinding = ItemStorageCategoryBinding.inflate(
                layoutInflater, binding.categoryContainer, false
            )
            val name = getString(category.nameRes)
            itemBinding.categoryName.text = name
            val fileLabel = if (category.fileCount == 1)
                getString(R.string.file_count_one)
            else
                getString(R.string.file_count_many, category.fileCount)
            itemBinding.categorySize.text =
                "${FileUtils.formatSize(requireContext(), category.sizeBytes)} · $fileLabel"

            itemBinding.root.setOnClickListener {
                val resolvedIndex = StorageCategories.all.indexOfFirst { it.nameRes == category.nameRes }
                StorageCategoryDetailFragment.newInstance(
                    label = name,
                    categoryIndex = resolvedIndex
                ).show(parentFragmentManager, "category_detail")
            }

            binding.categoryContainer.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressDialog?.dismiss()
        progressDialog = null
    }

    companion object {
        const val RESULT_KEY = "storage_breakdown_changed"
    }
}
