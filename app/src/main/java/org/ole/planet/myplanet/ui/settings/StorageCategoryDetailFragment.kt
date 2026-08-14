package org.ole.planet.myplanet.ui.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class StorageCategoryDetailFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentStorageCategoryDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StorageCategoryDetailViewModel by viewModels()
    private var categoryLabel: String = ""
    private var extensions: Set<String> = emptySet()
    private var allKnownExtensions: Set<String> = emptySet()

    private var items: List<OfflineResourceItem> = emptyList()
    private lateinit var adapter: ResourceAdapter

    companion object {
        private const val ARG_LABEL = "label"
        private const val ARG_EXTENSIONS = "extensions"
        private const val ARG_ALL_KNOWN = "all_known"
        const val RESULT_KEY = "category_deleted"
        const val PAYLOAD_CHECKED_CHANGED = "payload_checked_changed"

        fun newInstance(
            label: String,
            extensions: List<String>,
            allKnownExtensions: List<String>
        ) = StorageCategoryDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_LABEL, label)
                putStringArrayList(ARG_EXTENSIONS, ArrayList(extensions))
                putStringArrayList(ARG_ALL_KNOWN, ArrayList(allKnownExtensions))
            }
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

        val olePath = FileUtils.getOlePath(requireContext())
        viewModel.loadResources(olePath, extensions, allKnownExtensions)

        collectLatestWhenStarted(viewModel.items) { loaded ->
            if (loaded != null) {
                binding.progressBar.visibility = View.GONE

                if (loaded.isEmpty()) {
                    binding.emptyText.visibility = View.VISIBLE
                    return@collectLatestWhenStarted
                }

                items = loaded
                adapter.submitList(items)

                binding.resourceList.visibility = View.VISIBLE
                binding.actionButtons.visibility = View.VISIBLE
                binding.selectAllRow.visibility = View.VISIBLE
                binding.selectAllDivider.visibility = View.VISIBLE
                updateSelectionState()
            }
        }
    }

    private fun updateSelectionState() {
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
        binding.deleteSelectedButton.isEnabled = false
        binding.deleteAllButton.isEnabled = false

        val olePath = FileUtils.getOlePath(requireContext())
        viewModel.deleteResources(olePath, toDelete) {
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
            dismiss()
        }
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
