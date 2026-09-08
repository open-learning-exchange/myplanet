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
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.databinding.FragmentLibraryFilterBinding
import java.util.Locale

class ResourcesFilterFragment : DialogFragment(), AdapterView.OnItemClickListener {
    enum class FilterCategory {
        SUBJECTS, LANGUAGES, MEDIUMS, LEVELS, NONE
    }

    private var _binding: FragmentLibraryFilterBinding? = null
    private val binding get() = _binding!!
    var languages: Set<String>? = null
    var subjects: Set<String>? = null
    var mediums: Set<String>? = null
    var levels: Set<String>? = null
    private var filterListener: OnFilterListener? = null
    internal var selectedLang: MutableSet<String> = HashSet()
    internal var selectedSubs: MutableSet<String> = HashSet()
    internal var selectedMeds: MutableSet<String> = HashSet()
    internal var selectedLvls: MutableSet<String> = HashSet()
    internal var isSubjectsExpanded = false
    internal var isLanguagesExpanded = false
    internal var isMediumsExpanded = false
    internal var isLevelsExpanded = false
    internal var activeCategory: FilterCategory = FilterCategory.NONE

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
            onSectionHeaderClicked(FilterCategory.SUBJECTS)
        }
        binding.languagesLayout.setOnClickListener {
            onSectionHeaderClicked(FilterCategory.LANGUAGES)
        }
        binding.mediumsLayout.setOnClickListener {
            onSectionHeaderClicked(FilterCategory.MEDIUMS)
        }
        binding.levelsLayout.setOnClickListener {
            onSectionHeaderClicked(FilterCategory.LEVELS)
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
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                params.width = (resources.displayMetrics.widthPixels * 0.55).toInt()
                params.height = (resources.displayMetrics.heightPixels * 0.85).toInt()
                params.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            } else {
                params.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            window.attributes = params
        }
    }

    private fun initList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val data = filterListener?.getData()
            languages = data?.get("languages")
            subjects = data?.get("subjects")
            mediums = data?.get("mediums")
            levels = data?.get("levels")
            val selectedFilter = filterListener?.getSelectedFilter()
            selectedLvls = selectedFilter?.get("levels")?.toMutableSet() ?: selectedLvls
            selectedSubs = selectedFilter?.get("subjects")?.toMutableSet() ?: selectedSubs
            selectedMeds = selectedFilter?.get("mediums")?.toMutableSet() ?: selectedMeds
            selectedLang = selectedFilter?.get("languages")?.toMutableSet() ?: selectedLang

            activeCategory = when {
                selectedSubs.isNotEmpty() -> {
                    selectedLang.clear()
                    selectedMeds.clear()
                    selectedLvls.clear()
                    FilterCategory.SUBJECTS
                }
                selectedLang.isNotEmpty() -> {
                    selectedSubs.clear()
                    selectedMeds.clear()
                    selectedLvls.clear()
                    FilterCategory.LANGUAGES
                }
                selectedMeds.isNotEmpty() -> {
                    selectedSubs.clear()
                    selectedLang.clear()
                    selectedLvls.clear()
                    FilterCategory.MEDIUMS
                }
                selectedLvls.isNotEmpty() -> {
                    selectedSubs.clear()
                    selectedLang.clear()
                    selectedMeds.clear()
                    FilterCategory.LEVELS
                }
                else -> FilterCategory.NONE
            }

            if (selectedFilter != null && (selectedFilter.values.count { it.isNotEmpty() } > 1)) {
                filterListener?.filter(selectedSubs, selectedLang, selectedMeds, selectedLvls)
            }

            setAdapter(binding.listLevel, levels, selectedLvls)
            setAdapter(binding.listLang, languages, selectedLang)
            setAdapter(binding.listMedium, mediums, selectedMeds, ::getMediumDisplayName)
            setAdapter(binding.listSub, subjects, selectedSubs)
        }
    }

    private fun setAdapter(listView: ListView, ar: Set<String>?, set: Set<String>, label: (String) -> String = { it }, ) {
        val arr = ar?.let { ArrayList(it) } ?: return
        listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
        listView.adapter = object : ArrayAdapter<String>(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, arr) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<CheckedTextView>(R.id.checkBoxRowLayout)
                val item = getItem(position)
                if (item != null && textView != null) {
                    textView.text = label(item)
                }
                return view
            }
        }
        for (i in arr.indices) {
            listView.setItemChecked(i, set.contains(arr[i]))
        }
    }

    private fun onSectionHeaderClicked(category: FilterCategory) {
        val isCurrentlyExpanded = isSectionExpanded(category)
        if (isCurrentlyExpanded) {
            collapseCategorySection(category)
        } else {
            if (activeCategory != category && activeCategory != FilterCategory.NONE) {
                resetFilters(category)
            }
            activeCategory = category
            collapseAllSectionsExcept(category)
            expandCategorySection(category)
        }
    }

    private fun isSectionExpanded(category: FilterCategory): Boolean = when (category) {
        FilterCategory.SUBJECTS -> isSubjectsExpanded
        FilterCategory.LANGUAGES -> isLanguagesExpanded
        FilterCategory.MEDIUMS -> isMediumsExpanded
        FilterCategory.LEVELS -> isLevelsExpanded
        FilterCategory.NONE -> false
    }

    private fun collapseCategorySection(category: FilterCategory) {
        when (category) {
            FilterCategory.SUBJECTS -> {
                if (!binding.expandableLayoutSubjects.isGone) {
                    collapse(binding.expandableLayoutSubjects, binding.subjectsLayout)
                }
                isSubjectsExpanded = false
            }
            FilterCategory.LANGUAGES -> {
                if (!binding.expandableLayoutLanguages.isGone) {
                    collapse(binding.expandableLayoutLanguages, binding.languagesLayout)
                }
                isLanguagesExpanded = false
            }
            FilterCategory.MEDIUMS -> {
                if (!binding.expandableLayoutMediums.isGone) {
                    collapse(binding.expandableLayoutMediums, binding.mediumsLayout)
                }
                isMediumsExpanded = false
            }
            FilterCategory.LEVELS -> {
                if (!binding.expandableLayoutLevels.isGone) {
                    collapse(binding.expandableLayoutLevels, binding.levelsLayout)
                }
                isLevelsExpanded = false
            }
            FilterCategory.NONE -> {}
        }
    }

    private fun expandCategorySection(category: FilterCategory) {
        when (category) {
            FilterCategory.SUBJECTS -> {
                expand(binding.expandableLayoutSubjects, binding.listSub, binding.subjectsLayout)
                isSubjectsExpanded = true
            }
            FilterCategory.LANGUAGES -> {
                expand(binding.expandableLayoutLanguages, binding.listLang, binding.languagesLayout)
                isLanguagesExpanded = true
            }
            FilterCategory.MEDIUMS -> {
                expand(binding.expandableLayoutMediums, binding.listMedium, binding.mediumsLayout)
                isMediumsExpanded = true
            }
            FilterCategory.LEVELS -> {
                expand(binding.expandableLayoutLevels, binding.listLevel, binding.levelsLayout)
                isLevelsExpanded = true
            }
            FilterCategory.NONE -> {}
        }
    }

    private fun collapseAllSectionsExcept(keepCategory: FilterCategory) {
        if (keepCategory != FilterCategory.SUBJECTS && isSubjectsExpanded) {
            collapseCategorySection(FilterCategory.SUBJECTS)
        }
        if (keepCategory != FilterCategory.LANGUAGES && isLanguagesExpanded) {
            collapseCategorySection(FilterCategory.LANGUAGES)
        }
        if (keepCategory != FilterCategory.MEDIUMS && isMediumsExpanded) {
            collapseCategorySection(FilterCategory.MEDIUMS)
        }
        if (keepCategory != FilterCategory.LEVELS && isLevelsExpanded) {
            collapseCategorySection(FilterCategory.LEVELS)
        }
    }

    private fun resetFilters(keepCategory: FilterCategory) {
        if (keepCategory != FilterCategory.SUBJECTS) {
            selectedSubs.clear()
            clearListViewChoices(binding.listSub)
        }
        if (keepCategory != FilterCategory.LANGUAGES) {
            selectedLang.clear()
            clearListViewChoices(binding.listLang)
        }
        if (keepCategory != FilterCategory.MEDIUMS) {
            selectedMeds.clear()
            clearListViewChoices(binding.listMedium)
        }
        if (keepCategory != FilterCategory.LEVELS) {
            selectedLvls.clear()
            clearListViewChoices(binding.listLevel)
        }
        filterListener?.filter(selectedSubs, selectedLang, selectedMeds, selectedLvls)
    }

    private fun clearListViewChoices(listView: ListView) {
        listView.clearChoices()
        for (i in 0 until listView.count) {
            listView.setItemChecked(i, false)
        }
        (listView.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
        if (filterListener == null) return
        val s = adapterView.getItemAtPosition(i) as String
        val clickedCategory = when (adapterView.id) {
            R.id.list_lang -> FilterCategory.LANGUAGES
            R.id.list_sub -> FilterCategory.SUBJECTS
            R.id.list_level -> FilterCategory.LEVELS
            R.id.list_medium -> FilterCategory.MEDIUMS
            else -> return
        }

        if (activeCategory != clickedCategory && activeCategory != FilterCategory.NONE) {
            resetFilters(clickedCategory)
        }
        activeCategory = clickedCategory

        val isSelected = when (clickedCategory) {
            FilterCategory.LANGUAGES -> {
                addToList(s, selectedLang)
                selectedLang.contains(s)
            }
            FilterCategory.SUBJECTS -> {
                addToList(s, selectedSubs)
                selectedSubs.contains(s)
            }
            FilterCategory.LEVELS -> {
                addToList(s, selectedLvls)
                selectedLvls.contains(s)
            }
            FilterCategory.MEDIUMS -> {
                addToList(s, selectedMeds)
                selectedMeds.contains(s)
            }
            FilterCategory.NONE -> false
        }

        (adapterView as? ListView)?.setItemChecked(i, isSelected)

        if (selectedSubs.isEmpty() && selectedLang.isEmpty() && selectedMeds.isEmpty() && selectedLvls.isEmpty()) {
            activeCategory = FilterCategory.NONE
        }

        filterListener?.filter(selectedSubs, selectedLang, selectedMeds, selectedLvls)
    }

    private fun addToList(s: String, list: MutableSet<String>) {
        if (list.contains(s)) list.remove(s) else list.add(s)
    }

    private fun expand(view: View, listView: ListView, headerTextView: TextView) {
        val count = listView.adapter?.count ?: 0
        val itemHeight = 100
        val topPadding = 80
        val targetHeight = if (count < 6) {
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

    fun getMediumDisplayName(medium: String): String {
        return when (medium.lowercase(Locale.getDefault())) {
            "pdf" -> getString(R.string.filter_pdfs)
            "video" -> getString(R.string.filter_videos)
            "audio" -> getString(R.string.filter_audio)
            "image" -> getString(R.string.storage_images)
            "text/html" -> getString(R.string.medium_text_html)
            "html" -> getString(R.string.medium_html)
            "other" -> getString(R.string.other)
            else -> medium
        }
    }
}
