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
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.utilities.KeyboardUtils

@AndroidEntryPoint
class CollectionsFragment : DialogFragment(), TagAdapter.OnTagClickListener, CompoundButton.OnCheckedChangeListener {
    private var _binding: FragmentCollectionsBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var tagRepository: TagsRepository
    private lateinit var list: List<RealmTag>
    private lateinit var childMap: HashMap<String, List<RealmTag>>
    private var filteredList: ArrayList<RealmTag> = ArrayList()
    private lateinit var adapter: TagAdapter
    private var dbType: String? = null
    private var listener: TagClickListener? = null
    private var selectedItemsList: ArrayList<RealmTag> = ArrayList()
    private var textWatcher: TextWatcher? = null
    private var currentTagDataList = mutableListOf<TagData>()

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
        setListAdapter()
        setListeners()
    }

    private fun setListeners() {
        binding.btnOk.setOnClickListener {
            listener?.onOkClicked(selectedItemsList)
            dismiss()
        }
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {
                charSequence?.let { filterTags(it.toString()) }
            }
            override fun afterTextChanged(editable: Editable?) {}
        }
        binding.etFilter.addTextChangedListener(textWatcher)
    }

    private fun filterTags(charSequence: String) {
        val filteredParentList = if (charSequence.isEmpty()) {
            list
        } else {
            list.filter {
                it.name?.lowercase(Locale.ROOT)?.contains(charSequence.lowercase(Locale.ROOT)) == true
            }
        }
        currentTagDataList = buildTagDataList(filteredParentList).toMutableList()
        adapter.submitList(currentTagDataList)
    }

    private fun setListAdapter() {
        viewLifecycleOwner.lifecycleScope.launch {
            list = tagRepository.getTags(dbType)
            selectedItemsList = ArrayList(recentList)
            childMap = tagRepository.buildChildMap()
            adapter = TagAdapter(this@CollectionsFragment)
            binding.listTags.adapter = adapter
            currentTagDataList = buildTagDataList(list).toMutableList()
            adapter.submitList(currentTagDataList)
            binding.btnOk.visibility = View.VISIBLE
        }
    }

    private fun buildTagDataList(parents: List<RealmTag>): List<TagData> {
        val tagDataList = mutableListOf<TagData>()
        val isSelectMultiple = MainApplication.isCollectionSwitchOn
        for (parentTag in parents) {
            val isSelected = selectedItemsList.any { it.id == parentTag.id }
            val parent = (currentTagDataList.find { it is TagData.Parent && it.tag.id == parentTag.id } as? TagData.Parent)
                ?: TagData.Parent(parentTag, false, isSelected, isSelectMultiple)

            tagDataList.add(parent.copy(isSelected = isSelected, isSelectMultiple = isSelectMultiple))

            if (parent.isExpanded) {
                childMap[parent.tag.id]?.forEach { childTag ->
                    val isChildSelected = selectedItemsList.any { it.id == childTag.id }
                    tagDataList.add(TagData.Child(childTag, isChildSelected, isSelectMultiple))
                }
            }
        }
        return tagDataList
    }

    override fun onTagClicked(tag: RealmTag) {
        listener?.onTagSelected(tag)
        dismiss()
    }

    override fun onParentTagClicked(parent: TagData.Parent) {
        parent.isExpanded = !parent.isExpanded
        currentTagDataList = buildTagDataList(list).toMutableList()
        adapter.submitList(currentTagDataList.toList())
    }

    override fun onCheckboxTagSelected(tags: RealmTag) {
        if (selectedItemsList.contains(tags)) {
            selectedItemsList.remove(tags)
        } else {
            selectedItemsList.add(tags)
        }
        currentTagDataList = buildTagDataList(list).toMutableList()
        adapter.submitList(currentTagDataList)
    }

    override fun hasChildren(tagId: String?): Boolean {
        return childMap.containsKey(tagId)
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        MainApplication.isCollectionSwitchOn = b
        currentTagDataList = buildTagDataList(list).toMutableList()
        adapter.submitList(currentTagDataList)
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
