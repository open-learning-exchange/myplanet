package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.databinding.FragmentCollectionsBinding
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.utilities.KeyboardUtils

@AndroidEntryPoint
class CollectionsFragment : DialogFragment(), TagRecyclerAdapter.OnClickTagItem, CompoundButton.OnCheckedChangeListener {
    private var _binding: FragmentCollectionsBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var tagRepository: TagRepository
    private var allTags: List<RealmTag> = emptyList()
    private var tagAdapterItems = mutableListOf<TagAdapterItem>()
    private val expandedParentIds = mutableSetOf<String>()
    private lateinit var adapter: TagRecyclerAdapter
    private var dbType: String? = null
    private var listener: TagClickListener? = null
    private var selectedItemsList: ArrayList<RealmTag> = ArrayList()
    private var textWatcher: TextWatcher? = null
    private var childMap: HashMap<String, List<RealmTag>> = HashMap()

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
        setupRecyclerView()
        loadTags()
        setListeners()
    }

    private fun setupRecyclerView() {
        adapter = TagRecyclerAdapter(this)
        binding.listTags.layoutManager = LinearLayoutManager(requireContext())
        binding.listTags.adapter = adapter
    }

    private fun loadTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            allTags = tagRepository.getTags(dbType)
            childMap = tagRepository.buildChildMap()
            selectedItemsList = ArrayList(recentList)
            adapter.setSelectMultiple(true)
            adapter.setSelectedItems(selectedItemsList)
            buildAndSubmitList()
            binding.btnOk.visibility = View.VISIBLE
        }
    }

    private fun buildAndSubmitList(filterText: String = "") {
        val filteredParents = allTags.filter { tag ->
            val name = tag.name?.lowercase(Locale.ROOT) ?: ""
            name.contains(filterText) || childMap[tag.id]?.any { child ->
                child.name?.lowercase(Locale.ROOT)?.contains(filterText) == true
            } == true
        }

        tagAdapterItems = filteredParents.map { parentTag ->
            val children = childMap[parentTag.id]?.map { TagAdapterItem.Child(it) } ?: emptyList()
            TagAdapterItem.Parent(parentTag, children).apply {
                isExpanded = expandedParentIds.contains(parentTag.id)
            }
        }.toMutableList()
        updateAdapterList()
    }

    private fun updateAdapterList() {
        val displayList = mutableListOf<TagAdapterItem>()
        tagAdapterItems.forEach { item ->
            if (item is TagAdapterItem.Parent) {
                displayList.add(item)
                if (item.isExpanded) {
                    displayList.addAll(item.children)
                }
            }
        }
        adapter.submitList(displayList)
    }

    private fun setListeners() {
        binding.btnOk.setOnClickListener {
            listener?.onOkClicked(selectedItemsList)
            dismiss()
        }
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {
                buildAndSubmitList(charSequence.toString().lowercase(Locale.ROOT))
            }
            override fun afterTextChanged(editable: Editable?) {}
        }
        binding.etFilter.addTextChangedListener(textWatcher)
    }

    override fun onTagClicked(tag: RealmTag) {
        listener?.onTagSelected(tag)
        dismiss()
    }

    override fun onCheckboxTagSelected(tag: RealmTag, isChecked: Boolean) {
        if (isChecked) {
            if (!selectedItemsList.any { it.id == tag.id }) {
                selectedItemsList.add(tag)
            }
        } else {
            selectedItemsList.removeAll { it.id == tag.id }
        }
        adapter.setSelectedItems(selectedItemsList)
    }

    override fun onParentTagClicked(parent: TagAdapterItem.Parent) {
        parent.isExpanded = !parent.isExpanded
        if (parent.isExpanded) {
            expandedParentIds.add(parent.id)
        } else {
            expandedParentIds.remove(parent.id)
        }
        updateAdapterList()
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        MainApplication.isCollectionSwitchOn = b
        adapter.setSelectMultiple(b)
        binding.btnOk.visibility = if (b) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.etFilter?.removeTextChangedListener(textWatcher)
        textWatcher = null
        _binding = null
    }

    companion object {
        private lateinit var recentList: MutableList<RealmTag>
        @JvmStatic
        fun getInstance(l: MutableList<RealmTag>, dbType: String): CollectionsFragment {
            recentList = l
            val f = CollectionsFragment()
            val b = Bundle()
            b.putString("dbType", dbType)
            f.arguments = b
            return f
        }
    }

    fun setListener(listener: TagClickListener) {
        this.listener = listener
    }
}
