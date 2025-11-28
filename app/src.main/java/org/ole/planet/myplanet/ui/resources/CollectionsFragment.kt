package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
class CollectionsFragment : DialogFragment(), TagAdapter.OnClickTagItem {
    private var _binding: FragmentCollectionsBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var tagRepository: TagRepository
    private lateinit var list: List<RealmTag>
    private lateinit var adapter: TagAdapter
    private var dbType: String? = null
    private var listener: TagClickListener? = null
    private var selectedItemsList: ArrayList<RealmTag> = ArrayList()
    private var textWatcher: TextWatcher? = null
    private val tagList = mutableListOf<TagListItem>()

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
        val filteredList = if (charSequence.isEmpty()) {
            tagList
        } else {
            tagList.filter {
                when (it) {
                    is TagListItem.Parent -> it.tag.name?.lowercase(Locale.ROOT)?.contains(charSequence.lowercase(Locale.ROOT)) == true
                    is TagListItem.Child -> it.tag.name?.lowercase(Locale.ROOT)?.contains(charSequence.lowercase(Locale.ROOT)) == true
                }
            }
        }
        adapter.submitList(filteredList)
    }

    private fun setListAdapter() {
        viewLifecycleOwner.lifecycleScope.launch {
            list = tagRepository.getTags(dbType)
            selectedItemsList = ArrayList(recentList)
            tagList.clear()
            list.forEach { tag ->
                tagList.add(TagListItem.Parent(tag))
            }
            adapter = TagAdapter(selectedItemsList, this@CollectionsFragment)
            binding.listTags.adapter = adapter
            binding.listTags.layoutManager = LinearLayoutManager(requireContext())
            adapter.setSelectMultiple(true)
            adapter.submitList(tagList)
            binding.btnOk.visibility = View.VISIBLE
        }
    }

    override fun onTagClicked(tag: RealmTag) {
        listener?.onTagSelected(tag)
        dismiss()
    }

    override fun onCheckboxTagSelected(tag: RealmTag) {
        if (selectedItemsList.contains(tag)) {
            selectedItemsList.remove(tag)
        } else {
            selectedItemsList.add(tag)
        }
        adapter.notifyDataSetChanged()
    }

    override fun onParentTagClicked(parent: TagListItem.Parent, position: Int) {
        parent.isExpanded = !parent.isExpanded
        val currentList = adapter.currentList.toMutableList()
        if (parent.isExpanded) {
            val children = parent.tag.tags.map { TagListItem.Child(it) }
            currentList.addAll(position + 1, children)
        } else {
            currentList.removeAll { it is TagListItem.Child && it.tag.parentId == parent.tag.id }
        }
        adapter.submitList(currentList)
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
