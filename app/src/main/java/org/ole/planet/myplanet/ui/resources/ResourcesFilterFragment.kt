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
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.databinding.FragmentLibraryFilterBinding

class ResourcesFilterFragment : DialogFragment(), AdapterView.OnItemClickListener {
    private var _binding: FragmentLibraryFilterBinding? = null
    private val binding get() = _binding!!
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
        _binding = FragmentLibraryFilterBinding.inflate(inflater, container, false)
        binding.listMedium.onItemClickListener = this
        binding.listLang.onItemClickListener = this
        binding.listLevel.onItemClickListener = this
        binding.listSub.onItemClickListener = this
        binding.ivClose.setOnClickListener { dismiss() }
        binding.subjectsLayout.setOnClickListener {
            toggleSection(
                binding.expandableLayoutSubjects,
                binding.listSub,
                binding.subjectsLayout
            )
            isSubjectsExpanded = !isSubjectsExpanded
        }
        binding.languagesLayout.setOnClickListener {
            toggleSection(
                binding.expandableLayoutLanguages,
                binding.listLang,
                binding.languagesLayout
            )
            isLanguagesExpanded = !isLanguagesExpanded
        }
        binding.mediumsLayout.setOnClickListener {
            toggleSection(
                binding.expandableLayoutMediums,
                binding.listMedium,
                binding.mediumsLayout
            )
            isMediumsExpanded = !isMediumsExpanded
        }
        binding.levelsLayout.setOnClickListener {
            toggleSection(
                binding.expandableLayoutLevels,
                binding.listLevel,
                binding.levelsLayout
            )
            isLevelsExpanded = !isLevelsExpanded
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initList()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val params = window.attributes
            params.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = params
        }
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
        setAdapter(binding.listLevel, levels, selectedLvls)
        setAdapter(binding.listLang, languages, selectedLang)
        setAdapter(binding.listMedium, mediums, selectedMeds)
        setAdapter(binding.listSub, subjects, selectedSubs)
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
        }
    }

    private fun addToList(s: String, list: MutableSet<String>) {
        if (list.contains(s)) list.remove(s) else list.add(s)
    }

    private fun toggleSection(section: View, listView: ListView, headerTextView: TextView) {
        if (section.isGone) {
            expand(section, listView, headerTextView)
        } else {
            collapse(section, headerTextView)
        }
    }

    private fun expand(view: View, listView: ListView, headerTextView: TextView) {
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
        rotateDrawable(headerTextView, 180f)
    }

    private fun collapse(view: View, headerTextView: TextView) {
        val finalHeight = view.height
        val animator = slideAnimator(view, finalHeight, 0)
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.visibility = View.GONE
            }
        })
        animator.start()
        rotateDrawable(headerTextView, 0f)
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

    private fun rotateDrawable(textView: TextView, rotation: Float) {
        val drawableRes = if (rotation == 180f) R.drawable.outline_keyboard_arrow_up_24 else R.drawable.down_arrow
        textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, drawableRes, 0)
    }
}
