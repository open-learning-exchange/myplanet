package org.ole.planet.myplanet.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentStorageCategoryDetailBinding
import org.ole.planet.myplanet.databinding.ItemDownloadedResourceBinding
import org.ole.planet.myplanet.model.OfflineResourceItem
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class StorageCategoryDetailFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentStorageCategoryDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StorageCategoryViewModel by viewModels()

    private var categoryLabel: String = ""
    private var categoryIndex: Int = -1

    private lateinit var adapter: ResourceAdapter

    companion object {
        private const val ARG_LABEL = "label"
        private const val ARG_CATEGORY_INDEX = "category_index"
        const val RESULT_KEY = "category_deleted"
        const val PAYLOAD_CHECKED_CHANGED = "payload_checked_changed"

        fun newInstance(label: String, categoryIndex: Int) = StorageCategoryDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_LABEL, label)
                putInt(ARG_CATEGORY_INDEX, categoryIndex)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryLabel = arguments?.getString(ARG_LABEL) ?: ""
        categoryIndex = arguments?.getInt(ARG_CATEGORY_INDEX, -1) ?: -1
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
        _binding = FragmentStorageCategoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.categoryTitle.text = categoryLabel
        binding.closeButton.setOnClickListener { dismiss() }

        adapter = ResourceAdapter { clickedItem ->
            viewModel.toggleItemChecked(clickedItem.resourceId)
        }
        binding.resourceList.layoutManager = LinearLayoutManager(requireContext())
        binding.resourceList.adapter = adapter

        binding.selectAllRow.setOnClickListener {
            viewModel.toggleAllChecked()
        }

        binding.deleteSelectedButton.setOnClickListener {
            val selected = viewModel.uiState.value.items.filter { it.isChecked }
            confirmDelete(selected.size, getString(R.string.storage_delete_selected_confirm, selected.size)) {
                deleteItems(selected)
            }
        }

        binding.deleteAllButton.setOnClickListener {
            val items = viewModel.uiState.value.items
            confirmDelete(items.size, getString(R.string.storage_delete_confirm, categoryLabel)) {
                deleteItems(items)
            }
        }

        observeViewModel()
        val category = StorageCategories.all.getOrNull(categoryIndex)
        viewModel.loadResources(
            extensions = category?.extensions ?: emptySet(),
            allKnownExtensions = StorageCategories.allKnownExtensions
        )
    }

    private fun observeViewModel() {
        collectWhenStarted(viewModel.uiState) { state ->
            if (_binding == null) return@collectWhenStarted

            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

            if (state.isEmpty) {
                binding.emptyText.visibility = View.VISIBLE
                binding.resourceList.visibility = View.GONE
                binding.actionButtons.visibility = View.GONE
                binding.selectAllRow.visibility = View.GONE
                binding.selectAllDivider.visibility = View.GONE
                adapter.submitList(emptyList())
            } else if (!state.isLoading) {
                binding.emptyText.visibility = View.GONE
                binding.resourceList.visibility = View.VISIBLE
                binding.actionButtons.visibility = View.VISIBLE
                binding.selectAllRow.visibility = View.VISIBLE
                binding.selectAllDivider.visibility = View.VISIBLE

                adapter.submitList(state.items)
                updateSelectionState(state.items)
            }

            if (state.isDeleting) {
                binding.deleteSelectedButton.isEnabled = false
                binding.deleteAllButton.isEnabled = false
            }
        }

        collectWhenStarted(viewModel.deleteCompleteEvent) {
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
            dismiss()
        }
    }

    private fun updateSelectionState(items: List<OfflineResourceItem>) {
        if (_binding == null) return
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

    private fun deleteItems(toDelete: List<OfflineResourceItem>) {
        if (_binding == null) return
        viewModel.deleteItems(toDelete)
    }

    private val DIFF_CALLBACK = DiffUtils.itemCallback<OfflineResourceItem>(
        areItemsTheSame = { o, n -> o.resourceId == n.resourceId },
        areContentsTheSame = { o, n -> o == n },
        getChangePayload = { o, n -> if (o.copy(isChecked = n.isChecked) == n) PAYLOAD_CHECKED_CHANGED else null }
    )

    inner class ResourceAdapter(
        private val onItemClicked: (OfflineResourceItem) -> Unit
    ) : ListAdapter<OfflineResourceItem, ResourceAdapter.ViewHolder>(DIFF_CALLBACK) {

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
