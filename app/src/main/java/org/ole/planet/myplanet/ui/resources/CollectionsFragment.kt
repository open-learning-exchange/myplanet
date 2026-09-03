package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlin.collections.ArrayList
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnTagClickListener
import org.ole.planet.myplanet.databinding.FragmentCollectionsBinding
import org.ole.planet.myplanet.model.TagData
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.utils.KeyboardUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectLatestWhenStarted
import org.ole.planet.myplanet.utils.textChanges

@AndroidEntryPoint
class CollectionsFragment : DialogFragment(), OnTagClickListener, CompoundButton.OnCheckedChangeListener {
    private var _binding: FragmentCollectionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CollectionsViewModel by viewModels()

    private var list: List<TagEntity> = emptyList()
    private var childMap: Map<String, List<TagEntity>> = emptyMap()
    private lateinit var adapter: ResourcesTagsAdapter
    private var dbType: String? = null
    private var listener: OnTagClickListener? = null
    private var selectedItemsList: ArrayList<TagEntity> = ArrayList()
    private var currentTagDataList: List<TagData> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog_NoActionBar_MinWidth)
        dbType = arguments?.getString("dbType")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectionsBinding.inflate(inflater, container, false)
        KeyboardUtils.hideSoftKeyboard(requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ResourcesTagsAdapter(this@CollectionsFragment)
        binding.listTags.adapter = adapter
        selectedItemsList = ArrayList(recentList)

        viewModel.loadTags(dbType)

        collectLatestWhenStarted(viewModel.state) { state ->
            when (state) {
                is CollectionsState.Success -> {
                    list = state.list
                    childMap = state.childMap
                    val allTags = list + childMap.values.flatten()
                    val reconciledList = selectedItemsList.map { selected ->
                        allTags.find {
                            if (selected.id.isNotEmpty()) it.id == selected.id
                            else !selected.name.isNullOrEmpty() && it.name == selected.name
                        } ?: selected
                    }
                    selectedItemsList.clear()
                    selectedItemsList.addAll(reconciledList)
                    currentTagDataList = buildTagDataList(list)
                    adapter.submitList(currentTagDataList)
                    binding.btnOk.visibility = View.VISIBLE
                }
                is CollectionsState.Empty -> {
                    Utilities.toast(requireContext(), getString(R.string.no_data_available))
                    dismiss()
                }
                is CollectionsState.Error -> {
                    Utilities.toast(requireContext(), state.message)
                    dismiss()
                }
                is CollectionsState.Loading, CollectionsState.Idle -> {
                    // Ignore transient states without UI representation
                }
            }
        }

        setListeners()
    }

    private fun setListeners() {
        binding.btnOk.setOnClickListener {
            listener?.onOkClicked(selectedItemsList)
            dismiss()
        }
        binding.etFilter.textChanges()
            .debounce(300L)
            .distinctUntilChanged()
            .onEach { charSequence ->
                if (!::adapter.isInitialized) return@onEach
                charSequence?.let { filterTags(it.toString()) }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun filterTags(charSequence: String) {
        val filteredParentList = if (charSequence.isEmpty()) {
            list
        } else {
            list.filter {
                it.name?.lowercase(Locale.ROOT)?.contains(charSequence.lowercase(Locale.ROOT)) == true
            }
        }
        currentTagDataList = buildTagDataList(filteredParentList)
        adapter.submitList(currentTagDataList)
    }

    private fun buildTagDataList(parents: List<TagEntity>): List<TagData> {
        val tagDataList = mutableListOf<TagData>()
        val isSelectMultiple = MainApplication.isCollectionSwitchOn
        val selectedIds = selectedItemsList.mapNotNull { it.id.takeIf { id -> id.isNotEmpty() } }.toHashSet()
        val selectedNames = selectedItemsList.mapNotNull { it.name }.toHashSet()
        val parentMap = HashMap<String, TagData.Parent>()
        currentTagDataList.forEach {
            if (it is TagData.Parent && !parentMap.containsKey(it.tag.id)) {
                parentMap[it.tag.id] = it
            }
        }
        for (parentTag in parents) {
            val isSelected = selectedIds.contains(parentTag.id) || selectedNames.contains(parentTag.name)
            val parent = parentMap[parentTag.id] ?: TagData.Parent(parentTag, false, isSelected, isSelectMultiple)

            tagDataList.add(parent.copy(isSelected = isSelected, isSelectMultiple = isSelectMultiple))

            if (parent.isExpanded) {
                childMap[parent.tag.id]?.forEach { childTag ->
                    val isChildSelected = selectedIds.contains(childTag.id) || selectedNames.contains(childTag.name)
                    tagDataList.add(TagData.Child(childTag, isChildSelected, isSelectMultiple))
                }
            }
        }
        return tagDataList
    }

    override fun onTagClicked(tag: TagEntity) {
        listener?.onTagSelected(tag)
        dismiss()
    }

    override fun onParentTagClicked(parent: TagData.Parent) {
        parent.isExpanded = !parent.isExpanded
        currentTagDataList = buildTagDataList(list)
        adapter.submitList(currentTagDataList)
    }

    override fun onCheckboxTagSelected(tag: TagEntity) {
        val existingIndex = selectedItemsList.indexOfFirst { selected ->
            if (tag.id.isNotEmpty() && selected.id.isNotEmpty()) {
                selected.id == tag.id
            } else {
                !tag.name.isNullOrEmpty() && selected.name == tag.name
            }
        }
        if (existingIndex >= 0) {
            selectedItemsList.removeAt(existingIndex)
        } else {
            selectedItemsList.add(tag)
        }
        currentTagDataList = buildTagDataList(list)
        adapter.submitList(currentTagDataList)
    }

    override fun hasChildren(tagId: String?): Boolean {
        return childMap.containsKey(tagId)
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        MainApplication.isCollectionSwitchOn = b
        currentTagDataList = buildTagDataList(list)
        adapter.submitList(currentTagDataList)
        binding.btnOk.visibility = if (b) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private lateinit var recentList: MutableList<TagEntity>
        fun getInstance(l: MutableList<TagEntity>, dbType: String): CollectionsFragment {
            recentList = l
            val f = CollectionsFragment()
            val b = Bundle()
            b.putString("dbType", dbType)
            f.arguments = b
            return f
        }
    }

    fun setListener(listener: OnTagClickListener) {
        this.listener = listener
    }
}
