package org.ole.planet.myplanet.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentStorageBreakdownBinding
import org.ole.planet.myplanet.databinding.ItemStorageCategoryBinding
import org.ole.planet.myplanet.services.FreeSpaceWorker
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class StorageBreakdownFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentStorageBreakdownBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private var progressDialog: DialogUtils.CustomProgressDialog? = null
    private var loadJob: Job? = null

    internal data class CategoryData(
        @StringRes val nameRes: Int,
        val extensions: Set<String>,
        var sizeBytes: Long = 0,
        var fileCount: Int = 0
    )

    internal val categories: List<CategoryData> = StorageCategories.all.map {
        CategoryData(it.nameRes, it.extensions)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStorageBreakdownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Refresh when returning from the detail screen after a deletion
        parentFragmentManager.setFragmentResultListener(
            StorageCategoryDetailFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            loadStorage()
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
        }

        binding.freeUpSpaceButton.setOnClickListener {
            AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.are_you_sure_want_to_delete_all_the_files)
                .setPositiveButton(R.string.yes) { _, _ -> freeUpSpace() }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        loadStorage()
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
                                loadStorage()
                                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
                            }
                            WorkInfo.State.FAILED -> {
                                progressDialog.dismiss()
                                this@StorageBreakdownFragment.progressDialog = null
                                binding.freeUpSpaceButton.isEnabled = true
                                Utilities.toast(requireActivity(), getString(R.string.unable_to_clear_files))
                                loadStorage()
                                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
                            }
                            WorkInfo.State.CANCELLED -> {
                                progressDialog.dismiss()
                                this@StorageBreakdownFragment.progressDialog = null
                                binding.freeUpSpaceButton.isEnabled = true
                                loadStorage()
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

    private fun loadStorage() {
        loadJob?.cancel()

        binding.progressBar.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
        binding.emptyText.visibility = View.GONE

        binding.availableSpaceText.text = getString(R.string.available_space_colon) +
            " " + FileUtils.availableOverTotalMemoryFormattedString(requireContext())

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(dispatcherProvider.io) { scanStorage() }

            categories.forEachIndexed { index, category ->
                category.sizeBytes = result.sizes[index]
                category.fileCount = result.counts[index]
            }

            binding.progressBar.visibility = View.GONE

            if (result.totalBytes == 0L) {
                binding.emptyText.visibility = View.VISIBLE
                return@launch
            }

            binding.totalSizeText.text = getString(R.string.storage_total_downloaded) + ": " +
                FileUtils.formatSize(requireContext(), result.totalBytes)
            binding.contentLayout.visibility = View.VISIBLE
            populateCategoryRows()
        }
    }

    internal data class ScanResult(val totalBytes: Long, val sizes: LongArray, val counts: IntArray)

    private fun scanStorage(): ScanResult {
        return scanStorage(File(FileUtils.getOlePath(requireContext())))
    }

    internal fun scanStorage(oleDir: File): ScanResult {
        val sizes = LongArray(categories.size)
        val counts = IntArray(categories.size)

        if (!oleDir.exists() || !oleDir.isDirectory) return ScanResult(0L, sizes, counts)

        val extMap = buildMap {
            categories.forEachIndexed { index, category ->
                category.extensions.forEach { ext ->
                    put(ext, index)
                }
            }
        }

        var total = 0L

        oleDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val ext = file.extension
            val index = if (ext.isEmpty()) {
                StorageCategories.OTHER_INDEX
            } else {
                extMap[ext] ?: extMap[ext.lowercase()] ?: StorageCategories.OTHER_INDEX
            }
            val size = file.length()
            total += size
            sizes[index] += size
            counts[index]++
        }
        return ScanResult(total, sizes, counts)
    }

    private fun populateCategoryRows() {
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
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "storage_breakdown_changed"
    }
}
