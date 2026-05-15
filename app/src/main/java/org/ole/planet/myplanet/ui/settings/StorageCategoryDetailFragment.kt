package org.ole.planet.myplanet.ui.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import org.ole.planet.myplanet.databinding.FragmentStorageCategoryDetailBinding
import org.ole.planet.myplanet.databinding.ItemDownloadedResourceBinding
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.FileUtils

@AndroidEntryPoint
class StorageCategoryDetailFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentStorageCategoryDetailBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var resourcesRepository: ResourcesRepository

    private var categoryLabel: String = ""
    private var extensions: Set<String> = emptySet()
    private var allKnownExtensions: Set<String> = emptySet()

    data class ResourceItem(
        val resourceId: String,
        val title: String,
        val files: List<File>,
        val totalSizeBytes: Long,
        val isChecked: Boolean = false
    )

    private var items: List<ResourceItem> = emptyList()
    private lateinit var adapter: ResourceAdapter

    companion object {
        private const val ARG_LABEL = "label"
        private const val ARG_EXTENSIONS = "extensions"
        private const val ARG_ALL_KNOWN = "all_known"
        const val RESULT_KEY = "category_deleted"

        fun newInstance(
            label: String,
            extensions: List<String>,
            allKnownExtensions: List<String>
        ) = StorageCategoryDetailFragment().apply {
            arguments = bundleOf(
                ARG_LABEL to label,
                ARG_EXTENSIONS to ArrayList(extensions),
                ARG_ALL_KNOWN to ArrayList(allKnownExtensions)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryLabel = arguments?.getString(ARG_LABEL) ?: ""
        extensions = arguments?.getStringArrayList(ARG_EXTENSIONS)?.toSet() ?: emptySet()
        allKnownExtensions = arguments?.getStringArrayList(ARG_ALL_KNOWN)?.toSet() ?: emptySet()
    }

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
        _binding = FragmentStorageCategoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.categoryTitle.text = categoryLabel
        binding.closeButton.setOnClickListener { dismiss() }

        adapter = ResourceAdapter { clickedItem ->
            items = items.map {
                if (it.resourceId == clickedItem.resourceId) it.copy(isChecked = !it.isChecked) else it
            }
            adapter.submitList(items)
            updateSelectionState()
        }
        binding.resourceList.layoutManager = LinearLayoutManager(requireContext())
        binding.resourceList.adapter = adapter

        binding.selectAllRow.setOnClickListener {
            val allChecked = items.all { it.isChecked }
            items = items.map { it.copy(isChecked = !allChecked) }
            adapter.submitList(items)
            updateSelectionState()
        }

        binding.deleteSelectedButton.setOnClickListener {
            val selected = items.filter { it.isChecked }
            confirmDelete(selected.size, getString(R.string.storage_delete_selected_confirm, selected.size)) {
                deleteItems(selected)
            }
        }

        binding.deleteAllButton.setOnClickListener {
            confirmDelete(items.size, getString(R.string.storage_delete_confirm, categoryLabel)) {
                deleteItems(items)
            }
        }

        loadResources()
    }

    private fun loadResources() {
        binding.progressBar.visibility = View.VISIBLE
        binding.resourceList.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
        binding.actionButtons.visibility = View.GONE
        binding.selectAllRow.visibility = View.GONE
        binding.selectAllDivider.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { buildResourceItems() }
            binding.progressBar.visibility = View.GONE

            if (loaded.isEmpty()) {
                binding.emptyText.visibility = View.VISIBLE
                return@launch
            }

            items = loaded
            adapter.submitList(items)

            binding.resourceList.visibility = View.VISIBLE
            binding.actionButtons.visibility = View.VISIBLE
            binding.selectAllRow.visibility = View.VISIBLE
            binding.selectAllDivider.visibility = View.VISIBLE
        }
    }

    private suspend fun buildResourceItems(): List<ResourceItem> {
        val oleDir = File(FileUtils.getOlePath(requireContext()))
        if (!oleDir.exists() || !oleDir.isDirectory) return emptyList()

        // Build a map of resourceId → title from Realm (one query)
        val titleMap = resourcesRepository.getAllLibraries()
            .filter { it.resourceId != null }
            .associate { it.resourceId!! to (it.title ?: getString(R.string.storage_unknown_resource)) }

        // Group files by resourceId directory
        val grouped = mutableMapOf<String, MutableList<File>>()
        oleDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val ext = file.extension.lowercase()
            val matchesCategory = if (extensions.isEmpty()) {
                ext !in allKnownExtensions
            } else {
                ext in extensions
            }
            if (matchesCategory) {
                val resourceId = file.parentFile?.name ?: return@forEach
                grouped.getOrPut(resourceId) { mutableListOf() }.add(file)
            }
        }

        return grouped.map { (resourceId, files) ->
            val totalSize = files.sumOf { it.length() }
            val title = titleMap[resourceId] ?: getString(R.string.storage_unknown_resource)
            ResourceItem(resourceId, title, files, totalSize)
        }.sortedBy { it.title }
    }

    private fun updateSelectionState() {
        val checkedCount = items.count { it.isChecked }
        val allChecked = checkedCount == items.size && items.isNotEmpty()

        binding.selectAllCheckbox.isChecked = allChecked
        binding.deleteSelectedButton.isEnabled = checkedCount > 0

        if (checkedCount > 0) {
            binding.selectedCountText.text = getString(R.string.storage_selected_count, checkedCount)
            binding.selectedCountText.visibility = View.VISIBLE
        } else {
            binding.selectedCountText.visibility = View.GONE
        }
    }

    private fun confirmDelete(count: Int, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.are_you_sure)
            .setMessage(message)
            .setPositiveButton(R.string.yes) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun deleteItems(toDelete: List<ResourceItem>) {
        binding.deleteSelectedButton.isEnabled = false
        binding.deleteAllButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val oleDir = File(FileUtils.getOlePath(requireContext()))

                toDelete.forEach { item ->
                    item.files.forEach { it.delete() }
                    // Remove empty parent directory
                    val parentDir = oleDir.resolve(item.resourceId)
                    if (parentDir.exists() && parentDir.list().isNullOrEmpty()) {
                        parentDir.delete()
                    }
                }

                // Sync Realm: mark deleted resources as not offline
                val deletedIds = toDelete.map { it.resourceId }.toSet()
                val allResources = resourcesRepository.getAllLibraries()
                allResources.filter { it.resourceOffline && it.resourceId in deletedIds }.forEach { resource ->
                    val id = resource._id ?: return@forEach
                    resourcesRepository.updateLibraryItem(id) { it.resourceOffline = false }
                }
            }

            // Notify parent to refresh, then dismiss
            parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf())
            dismiss()
        }
    }

    inner class ResourceAdapter(
        private val onItemClicked: (ResourceItem) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<ResourceItem, ResourceAdapter.ViewHolder>(ResourceDiffCallback()) {

        inner class ViewHolder(val binding: ItemDownloadedResourceBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDownloadedResourceBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.binding.resourceTitle.text = item.title
            holder.binding.resourceSize.text = FileUtils.formatSize(requireContext(), item.totalSizeBytes)
            holder.binding.checkBox.isChecked = item.isChecked
            holder.binding.root.setOnClickListener {
                onItemClicked(item)
            }
            holder.binding.checkBox.setOnClickListener {
                onItemClicked(item)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isNotEmpty()) {
                val item = getItem(position)
                holder.binding.checkBox.isChecked = item.isChecked
                holder.binding.root.setOnClickListener { onItemClicked(item) }
                holder.binding.checkBox.setOnClickListener { onItemClicked(item) }
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }
    }

    private class ResourceDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<ResourceItem>() {
        override fun areItemsTheSame(oldItem: ResourceItem, newItem: ResourceItem): Boolean {
            return oldItem.resourceId == newItem.resourceId
        }

        override fun areContentsTheSame(oldItem: ResourceItem, newItem: ResourceItem): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: ResourceItem, newItem: ResourceItem): Any? {
            if (oldItem.copy(isChecked = newItem.isChecked) == newItem) {
                return true
            }
            return null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
