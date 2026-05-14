package org.ole.planet.myplanet.ui.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentStorageBreakdownBinding
import org.ole.planet.myplanet.databinding.ItemStorageCategoryBinding
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.FileUtils

@AndroidEntryPoint
class StorageBreakdownFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentStorageBreakdownBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var resourcesRepository: ResourcesRepository

    internal data class CategoryData(
        @StringRes val nameRes: Int,
        val extensions: Set<String>,
        var sizeBytes: Long = 0,
        var fileCount: Int = 0
    )

    internal val categories = listOf(
        CategoryData(R.string.storage_videos, setOf("mp4", "mkv", "avi", "webm", "mov", "3gp", "flv")),
        CategoryData(R.string.storage_audio, setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus")),
        CategoryData(R.string.storage_pdfs, setOf("pdf")),
        CategoryData(R.string.storage_images, setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")),
        CategoryData(R.string.storage_other, emptySet())
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { d: DialogInterface ->
            val sheet = (d as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                BottomSheetBehavior.from(it).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
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
        ) { _, _ -> loadStorage() }

        loadStorage()
    }

    private fun loadStorage() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
        binding.emptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val totalBytes = withContext(Dispatchers.IO) { scanStorage() }

            binding.progressBar.visibility = View.GONE

            if (totalBytes == 0L) {
                binding.emptyText.visibility = View.VISIBLE
                return@launch
            }

            binding.totalSizeText.text = getString(R.string.storage_total_downloaded) + ": " +
                FileUtils.formatSize(requireContext(), totalBytes)
            binding.contentLayout.visibility = View.VISIBLE
            populateCategoryRows()
        }
    }

    private fun scanStorage(): Long {
        categories.forEach { it.sizeBytes = 0; it.fileCount = 0 }

        val oleDir = File(FileUtils.getOlePath(requireContext()))
        if (!oleDir.exists() || !oleDir.isDirectory) return 0L

        val allKnownExtensions = categories.dropLast(1).flatMap { it.extensions }.toSet()
        var total = 0L

        oleDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val ext = file.extension.lowercase()
            val size = file.length()
            total += size
            val cat = categories.find { it.extensions.isNotEmpty() && ext in it.extensions }
                ?: categories.last()
            cat.sizeBytes += size
            cat.fileCount++
        }
        return total
    }

    private fun populateCategoryRows() {
        binding.categoryContainer.removeAllViews()
        val allKnownExtensions = categories.dropLast(1).flatMap { it.extensions }.toSet()

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
                StorageCategoryDetailFragment.newInstance(
                    label = name,
                    extensions = category.extensions.toList(),
                    allKnownExtensions = allKnownExtensions.toList()
                ).show(parentFragmentManager, "category_detail")
            }

            binding.categoryContainer.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
