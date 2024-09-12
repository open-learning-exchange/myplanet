package org.ole.planet.myplanet.ui.resources

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import androidx.fragment.app.DialogFragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.databinding.FragmentLibraryFilterBinding

class ResourcesFilterFragment : DialogFragment(), AdapterView.OnItemClickListener {
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
    private var isSubjectsExpanded = false
    private var isLanguagesExpanded = false
    private var isMediumsExpanded = false
    private var isLevelsExpanded = false

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
        fragmentLibraryFilterBinding.subjectsLayout.setOnClickListener {
            toggleSection(
                fragmentLibraryFilterBinding.expandableLayoutSubjects,
                fragmentLibraryFilterBinding.listSub,
                fragmentLibraryFilterBinding.subjectsDropdownIcon
            )
            isSubjectsExpanded = !isSubjectsExpanded
        }
        fragmentLibraryFilterBinding.languagesLayout.setOnClickListener {
            toggleSection(
                fragmentLibraryFilterBinding.expandableLayoutLanguages,
                fragmentLibraryFilterBinding.listLang,
                fragmentLibraryFilterBinding.languagesDropdownIcon
            )
            isLanguagesExpanded = !isLanguagesExpanded
        }
        fragmentLibraryFilterBinding.mediumsLayout.setOnClickListener {
            toggleSection(
                fragmentLibraryFilterBinding.expandableLayoutMediums,
                fragmentLibraryFilterBinding.listMedium,
                fragmentLibraryFilterBinding.mediumsDropdownIcon
            )
            isMediumsExpanded = !isMediumsExpanded
        }
        fragmentLibraryFilterBinding.levelsLayout.setOnClickListener {
            toggleSection(
                fragmentLibraryFilterBinding.expandableLayoutLevels,
                fragmentLibraryFilterBinding.listLevel,
                fragmentLibraryFilterBinding.levelsDropdownIcon
            )
            isLevelsExpanded = !isLevelsExpanded
        }
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

    private fun toggleSection(section: View, listView: ListView, dropdownIcon: ImageView) {
        if (section.visibility == View.GONE) {
            expand(section, listView, dropdownIcon)
        } else {
            collapse(section, dropdownIcon)
        }
    }

    private fun expand(view: View, listView: ListView, dropdownIcon: ImageView) {
        val count = listView.adapter.count
        val itemHeight = 100
        val topPadding = 80
        val targetHeight = if(count < 6){
            count * itemHeight + topPadding
        } else {
            5 * itemHeight + topPadding
        }
        view.layoutParams.height = 0
        view.visibility = View.VISIBLE
        val animator = slideAnimator(view, 0, targetHeight)
        animator.start()
        dropdownIcon.animate().rotation(180f).setDuration(300).start()
    }

    private fun collapse(view: View, dropdownIcon: ImageView) {
        val finalHeight = view.height
        val animator = slideAnimator(view, finalHeight, 0)
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.visibility = View.GONE
            }
        })
        animator.start()
        dropdownIcon.animate().rotation(0f).setDuration(300).start()
    }

    private fun slideAnimator(view: View, start: Int, end: Int): ValueAnimator {
        val animator = ValueAnimator.ofInt(start, end)
        animator.duration = 300
        animator.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = view.layoutParams
            layoutParams.height = value
            view.layoutParams = layoutParams
        }
        return animator
    }
}
