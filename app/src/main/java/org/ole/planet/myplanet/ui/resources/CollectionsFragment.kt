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
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.utilities.KeyboardUtils

@AndroidEntryPoint
class CollectionsFragment : DialogFragment(), TagExpandableAdapter.OnClickTagItem, CompoundButton.OnCheckedChangeListener {
    private var _binding: FragmentCollectionsBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var tagRepository: TagRepository
    private lateinit var list: List<RealmTag>
    private var filteredList: ArrayList<RealmTag> = ArrayList()
    private lateinit var adapter: TagExpandableAdapter
    private var dbType: String? = null
    private var listener: TagClickListener? = null
    private var selectedItemsList: ArrayList<RealmTag> = ArrayList()
    private var textWatcher: TextWatcher? = null

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
        filteredList.clear()
        if (charSequence.isEmpty()) {
            adapter.setTagList(list)
            return
        }
        list.forEach { t ->
            if (t.name?.lowercase(Locale.ROOT)?.contains(charSequence.lowercase(Locale.ROOT)) == true) {
                filteredList.add(t)
            }
        }
        adapter.setTagList(filteredList)
    }

    private fun setListAdapter() {
        viewLifecycleOwner.lifecycleScope.launch {
            list = tagRepository.getTags(dbType)
            selectedItemsList = ArrayList(recentList)
            val childMap = tagRepository.buildChildMap()
            binding.listTags.setGroupIndicator(null)
            adapter = TagExpandableAdapter(list, childMap, selectedItemsList)
            adapter.setSelectMultiple(true)
            adapter.setClickListener(this@CollectionsFragment)
            binding.listTags.setAdapter(adapter)
            binding.btnOk.visibility = View.VISIBLE
        }
    }

    override fun onTagClicked(tag: RealmTag) {
        listener?.onTagSelected(tag)
        dismiss()
    }

    override fun onCheckboxTagSelected(tags: RealmTag) {
        if (selectedItemsList.contains(tags)) {
            selectedItemsList.remove(tags)
        } else {
            selectedItemsList.add(tags)
        }
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        MainApplication.isCollectionSwitchOn = b
        adapter.setSelectMultiple(b)
        adapter.setTagList(list)
        binding.listTags.setAdapter(adapter)
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
