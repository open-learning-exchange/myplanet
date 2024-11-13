package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.DialogFragment
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.databinding.FragmentCollectionsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utilities.KeyboardUtils
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.List
import io.realm.Realm
import org.ole.planet.myplanet.R

class CollectionsFragment : DialogFragment(), TagExpandableAdapter.OnClickTagItem, CompoundButton.OnCheckedChangeListener {
    private lateinit var fragmentCollectionsBinding: FragmentCollectionsBinding
    private lateinit var mRealm: Realm
    private lateinit var list: List<RealmTag>
    private var filteredList: ArrayList<RealmTag> = ArrayList()
    private lateinit var adapter: TagExpandableAdapter
    private var dbType: String? = null
    private var listener: TagClickListener? = null
    private var selectedItemsList: ArrayList<RealmTag> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog_NoActionBar_MinWidth)
        dbType = arguments?.getString("dbType")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCollectionsBinding = FragmentCollectionsBinding.inflate(inflater, container, false)
        mRealm = DatabaseService().realmInstance
        KeyboardUtils.hideSoftKeyboard(requireActivity())
        return fragmentCollectionsBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListAdapter()
        setListeners()
    }

    private fun setListeners() {
        fragmentCollectionsBinding.btnOk.setOnClickListener {
            listener?.onOkClicked(selectedItemsList)
            dismiss()
        }
        fragmentCollectionsBinding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {
                charSequence?.let { filterTags(it.toString()) }
            }
            override fun afterTextChanged(editable: Editable?) {}
        })
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
        list = mRealm.where(RealmTag::class.java).equalTo("db", dbType).isNotEmpty("name").equalTo("isAttached", false).findAll()

        selectedItemsList = ArrayList(recentList)
        val allTags = mRealm.where(RealmTag::class.java).findAll()
        val childMap = HashMap<String, List<RealmTag>>()
        allTags.forEach { t -> createChildMap(childMap, t) }
        fragmentCollectionsBinding.listTags.setGroupIndicator(null)
        adapter = TagExpandableAdapter(list, childMap, selectedItemsList)
        adapter.setSelectMultiple(true)
        adapter.setClickListener(this)
        fragmentCollectionsBinding.listTags.setAdapter(adapter)
        fragmentCollectionsBinding.btnOk.visibility = View.VISIBLE
    }

    private fun createChildMap(childMap: HashMap<String, List<RealmTag>>, t: RealmTag) {
        t.attachedTo?.forEach { s ->
            val l: MutableList<RealmTag> = ArrayList()
            if (childMap.containsKey(s)) {
                childMap[s]?.let { l.addAll(it) }
            }
            if (!l.contains(t)) l.add(t)
            childMap[s] = l
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
        fragmentCollectionsBinding.listTags.setAdapter(adapter)
        fragmentCollectionsBinding.btnOk.visibility = if (b) View.VISIBLE else View.GONE
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