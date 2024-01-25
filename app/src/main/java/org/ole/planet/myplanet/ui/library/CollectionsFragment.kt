package org.ole.planet.myplanet.ui.library

import android.R
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.DialogFragment
import io.realm.Realm
import io.realm.RealmResults
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.databinding.FragmentCollectionsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.ui.library.TagExpandableAdapter.OnClickTagItem
import org.ole.planet.myplanet.utilities.KeyboardUtils.hideSoftKeyboard
import java.util.Locale

class CollectionsFragment : DialogFragment(), OnClickTagItem, CompoundButton.OnCheckedChangeListener {
    private var fragmentCollectionsBinding: FragmentCollectionsBinding? = null
    var mRealm: Realm? = null
    var list: List<RealmTag>? = null
    private var filteredList: MutableList<RealmTag>? = null
    var adapter: TagExpandableAdapter? = null
    private var dbType: String? = null
    private var tagListener: TagClickListener? = null
    private var selectedItemsList: ArrayList<RealmTag?>? = ArrayList()

    fun setListener(listener: TagClickListener?) {
        this.tagListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth)
        if (arguments != null) dbType = requireArguments().getString("dbType")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCollectionsBinding = FragmentCollectionsBinding.inflate(inflater, container, false)
        mRealm = DatabaseService(requireActivity()).realmInstance
        filteredList = ArrayList()
        hideSoftKeyboard(requireActivity())
        return fragmentCollectionsBinding!!.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setListAdapter()
        setListeners()
    }

    private fun setListeners() {
        fragmentCollectionsBinding!!.btnOk.setOnClickListener {
            if (tagListener != null) {
                tagListener!!.onOkClicked(selectedItemsList)
                dismiss()
            }
        }
        fragmentCollectionsBinding!!.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                filterTags(charSequence.toString())
            }

            override fun afterTextChanged(editable: Editable) {}
        })
    }

    private fun filterTags(charSequence: String) {
        filteredList!!.clear()
        if (charSequence.isEmpty()) {
            adapter!!.setTagList(list!!)
            return
        }
        for (t in list!!) {
            if (t.name!!.lowercase(Locale.getDefault()).contains(charSequence.lowercase(Locale.getDefault()))) {
                filteredList!!.add(t)
            }
        }
        adapter!!.setTagList(filteredList!!)
    }

    private fun setListAdapter() {
        list = mRealm!!.where(RealmTag::class.java).equalTo("db", dbType).isNotEmpty("name").equalTo("isAttached", false).findAll()
        selectedItemsList = recentList as ArrayList<RealmTag?>?
        val allTags: List<RealmTag> = mRealm!!.where(RealmTag::class.java).findAll()
        val childMap = HashMap<String?, MutableList<RealmTag>>()
        for (t in allTags) {
            createChildMap(childMap, t)
        }
        fragmentCollectionsBinding!!.listTags.setGroupIndicator(null)
        adapter = TagExpandableAdapter(list as RealmResults<RealmTag>, childMap, selectedItemsList)
        adapter!!.setSelectMultiple(true)
        adapter!!.setClickListener(this)
        fragmentCollectionsBinding!!.listTags.setAdapter(adapter)
        fragmentCollectionsBinding!!.btnOk.visibility = View.VISIBLE
    }

    private fun createChildMap(childMap: HashMap<String?, MutableList<RealmTag>>, t: RealmTag) {
        for (s in t.attachedTo!!) {
            var l: MutableList<RealmTag> = ArrayList()
            if (childMap.containsKey(s)) {
                l = childMap[s]!!
            }
            if (!l.contains(t)) l.add(t)
            childMap[s] = l
        }
    }

    override fun onTagClicked(tag: RealmTag?) {
        if (tagListener != null) tagListener!!.onTagSelected(tag)
        dismiss()
    }

    override fun onCheckboxTagSelected(tag: RealmTag?) {
        if (selectedItemsList!!.contains(tag)) {
            selectedItemsList!!.remove(tag)
        } else {
            selectedItemsList!!.add(tag)
        }
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        MainApplication.isCollectionSwitchOn = b
        adapter!!.setSelectMultiple(b)
        adapter!!.setTagList(list!!)
        fragmentCollectionsBinding!!.listTags.setAdapter(adapter)
        fragmentCollectionsBinding!!.btnOk.visibility = if (b) View.VISIBLE else View.GONE
    }

    companion object {
        var recentList: List<RealmTag>? = null
        fun getInstance(l: MutableList<RealmTag?>?, dbType: String?): CollectionsFragment {
            recentList = l as List<RealmTag>?
            val f = CollectionsFragment()
            val b = Bundle()
            b.putString("dbType", dbType)
            f.arguments = b
            return f
        }
    }
}
