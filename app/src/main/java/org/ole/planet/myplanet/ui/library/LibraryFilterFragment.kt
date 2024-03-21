package org.ole.planet.myplanet.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.DialogFragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.databinding.FragmentLibraryFilterBinding

class LibraryFilterFragment : DialogFragment(), AdapterView.OnItemClickListener {
    private lateinit var fragmentLibraryFilterBinding: FragmentLibraryFilterBinding
    var languages: Set<String>? = null
    var subjects: Set<String>? = null
    var mediums: Set<String>? = null
    var levels: Set<String>? = null
    private var filterListener: OnFilterListener? = null
    private var selectedLang: MutableSet<String> = HashSet()
    private var selectedSubs: MutableSet<String> = HashSet()
    private var selectedMeds: MutableSet<String> = HashSet()
    private var selectedLvls: MutableSet<String> = HashSet()

    fun setListener(listener: OnFilterListener?) {
        this.filterListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentLibraryFilterBinding = FragmentLibraryFilterBinding.inflate(inflater, container, false)
        fragmentLibraryFilterBinding.listMedium.onItemClickListener = this
        fragmentLibraryFilterBinding.listLang.onItemClickListener = this
        fragmentLibraryFilterBinding.listLevel.onItemClickListener = this
        fragmentLibraryFilterBinding.listSub.onItemClickListener = this
        fragmentLibraryFilterBinding.ivClose.setOnClickListener { dismiss() }
        return fragmentLibraryFilterBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initList()
    }

    private fun initList() {
        languages = filterListener?.getData()?.get("languages")
        subjects = filterListener?.getData()?.get("subjects")
        mediums = filterListener?.getData()?.get("mediums")
        levels = filterListener?.getData()?.get("levels")
        selectedLvls = filterListener?.getSelectedFilter()?.get("levels") as MutableSet<String>
        selectedSubs = filterListener?.getSelectedFilter()?.get("subjects") as MutableSet<String>
        selectedMeds = filterListener?.getSelectedFilter()?.get("mediums") as MutableSet<String>
        selectedLang = filterListener?.getSelectedFilter()?.get("languages") as MutableSet<String>
        setAdapter(fragmentLibraryFilterBinding.listLevel, levels, selectedLvls)
        setAdapter(fragmentLibraryFilterBinding.listLang, languages, selectedLang)
        setAdapter(fragmentLibraryFilterBinding.listMedium, mediums, selectedMeds)
        setAdapter(fragmentLibraryFilterBinding.listSub, subjects, selectedSubs)
    }

    private fun setAdapter(listView: ListView, ar: Set<String>?, set: Set<String>) {
        val arr = ar?.let { ArrayList(it) }
        listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
        listView.adapter = ArrayAdapter(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, arr!!)
        for (i in arr.indices) {
                listView.setItemChecked(i, set.contains(arr[i]))
            }
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
        if (filterListener != null) {
            val s = adapterView.getItemAtPosition(i) as String
            when (adapterView.id) {
                R.id.list_lang -> { addToList(s, selectedLang) }
                R.id.list_sub -> addToList(s, selectedSubs)
                R.id.list_level -> addToList(s, selectedLvls)
                R.id.list_medium -> addToList(s, selectedMeds)
            }
            filterListener?.filter(selectedSubs, selectedLang, selectedMeds, selectedLvls)
            initList()
        }
    }

    private fun addToList(s: String, list: MutableSet<String>) {
        if (list.contains(s)) list.remove(s) else list.add(s)
    }
}
