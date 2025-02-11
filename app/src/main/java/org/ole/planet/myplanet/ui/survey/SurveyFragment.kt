package org.ole.planet.myplanet.ui.survey

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.utilities.CustomSpinner

class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>() {
    private lateinit var addNewSurvey: FloatingActionButton
    private lateinit var spn: CustomSpinner
    private var isTitleAscending = true
    private lateinit var etSearch: EditText
    private lateinit var adapter: AdapterSurvey
    private var isTeam: Boolean = false
    private var teamId: String? = null

    override fun getLayout(): Int {
        return R.layout.fragment_survey
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTeam = arguments?.getBoolean("isTeam", false) == true
        teamId = arguments?.getString("teamId", null)
        adapter = AdapterSurvey(requireActivity(), mRealm, model?.id ?: "", isTeam, teamId)
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        return adapter
    }

    @SuppressLint("ResourceAsColor")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                adapter.updateData(filterSurvey(etSearch.text.toString()))
            }

            override fun afterTextChanged(s: Editable) {}
        })

        setupRecyclerView()
        setupListeners()
        updateAdapterData()
    }

    private fun initializeViews(view: View) {
        spn = view.findViewById(R.id.spn_sort)
        etSearch = requireView().findViewById(R.id.et_search)
        addNewSurvey = view.findViewById(R.id.fab_add_new_survey)
    }

    private fun setupRecyclerView() {
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        addNewSurvey.setOnClickListener {}

        spn.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                when (i) {
                    0 -> adapter.SortByDate(true)
                    1 -> adapter.SortByDate(false)
                    2 -> toggleTitleSortOrder()
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        spn.onSameItemSelected(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (i == 2) {
                    toggleTitleSortOrder()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
    }

    private fun updateAdapterData(sort: Sort = Sort.ASCENDING, field: String = "name") {
        val query = mRealm.where(RealmStepExam::class.java)

        val surveys = if (teamId != null && isTeam) {
            query.equalTo("teamId", teamId)
                .sort(field, sort)
                .findAll()
        } else {
            query.sort(field, sort)
                .findAll()
        }

        adapter.updateData(safeCastList(surveys, RealmStepExam::class.java))
        updateUIState()
    }

    private fun updateUIState() {
        val itemCount = adapter.itemCount
        spn.visibility = if (itemCount == 0) View.GONE else View.VISIBLE
        showNoData(tvMessage, itemCount, "survey")
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        val sort = if (isTitleAscending) Sort.ASCENDING else Sort.DESCENDING
        updateAdapterData(sort, "name")
    }

    private fun <T> safeCastList(items: List<Any?>, clazz: Class<T>): List<T> {
        return items.mapNotNull { it?.takeIf(clazz::isInstance)?.let(clazz::cast) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }

    companion object {
        fun newInstance(): SurveyFragment {
            return SurveyFragment()
        }
    }
}
