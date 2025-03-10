package org.ole.planet.myplanet.ui.survey

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.realm.Sort
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.SurveyAdoptListener
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.CustomSpinner

class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>(), SurveyAdoptListener {
    private lateinit var addNewSurvey: FloatingActionButton
    private lateinit var spn: CustomSpinner
    private lateinit var etSearch: EditText
    private var isTitleAscending = true
    private lateinit var adapter: AdapterSurvey
    private var isTeam: Boolean = false
    private var teamId: String? = null
    lateinit var rbTeamSurvey: RadioButton
    lateinit var rbAdoptSurvey: RadioButton
    lateinit var rgSurvey: RadioGroup

    override fun getLayout(): Int {
        return R.layout.fragment_survey
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTeam = arguments?.getBoolean("isTeam", false) == true
        teamId = arguments?.getString("teamId", null)
        profileDbHandler = UserProfileDbHandler(requireContext())
        val userProfileModel = profileDbHandler.userModel
        adapter = AdapterSurvey(requireActivity(), mRealm, userProfileModel?.id, isTeam, teamId, this)
    }

    override fun onSurveyAdopted() {
        updateAdapterData(isTeamShareAllowed = false)
        rbTeamSurvey.isChecked = true
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
                updateAdapterData(isTeamShareAllowed = false)
                recyclerView.scrollToPosition(0)
            }

            override fun afterTextChanged(s: Editable) {}
        })
        setupRecyclerView()
        setupListeners()
        updateAdapterData(isTeamShareAllowed = false)
        showHideRadioButton()
    }

    private fun showHideRadioButton() {
        if (isTeam) {
            rgSurvey.visibility = View.VISIBLE
            rbTeamSurvey.isChecked = true
        }
    }

    private fun initializeViews(view: View) {
        spn = view.findViewById(R.id.spn_sort)
        val adapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.sort_by_date, R.layout.spinner_text)
        adapter.setDropDownViewResource(R.layout.spinner_text)
        spn.adapter = adapter
        etSearch = requireView().findViewById(R.id.et_search)
        addNewSurvey = view.findViewById(R.id.fab_add_new_survey)
        rbTeamSurvey = view.findViewById(R.id.rbTeamSurvey)
        rbAdoptSurvey = view.findViewById(R.id.rbAdoptSurvey)
        rgSurvey = view.findViewById(R.id.rgSurvey)
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
                    2 -> adapter.toggleTitleSortOrder()
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        spn.onSameItemSelected(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (i == 2) {
                    adapter.toggleTitleSortOrder()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        rbAdoptSurvey.setOnClickListener {
            updateAdapterData(isTeamShareAllowed = true)
        }

        rbTeamSurvey.setOnClickListener {
            updateAdapterData(isTeamShareAllowed = false)
        }
    }

    private fun search(s: String, list: List<RealmStepExam>): List<RealmStepExam> {
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQuery = normalizeText(s)
        val startsWithQuery = mutableListOf<RealmStepExam>()
        val containsQuery = mutableListOf<RealmStepExam>()

        for (item in list) {
            val title = item.name?.let { normalizeText(it) }
            if(title == null) continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (queryParts.all { title.contains(normalizeText(it), ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    fun updateAdapterData(sort: Sort = Sort.ASCENDING, field: String = "name", isTeamShareAllowed: Boolean) {
        val submissionQuery = mRealm.where(RealmSubmission::class.java)
            .isNotNull("membershipDoc")
            .findAll()

        val query = mRealm.where(RealmStepExam::class.java)

        if (isTeamShareAllowed) {
            val filteredParentIds = submissionQuery
                .mapNotNull { submission ->
                    val parentJson = JSONObject(submission.parent ?: "{}")
                    parentJson.optString("_id")
                }
                .filter { it.isNotEmpty() }
                .toSet()

            query.beginGroup()
                .equalTo("isTeamShareAllowed", true)
                .and()
                .not()
                .`in`("id", filteredParentIds.toTypedArray())
            query.endGroup()
        } else if (!teamId.isNullOrEmpty() && isTeam) {
            val teamSpecificExams = submissionQuery
                .filter { it.membershipDoc?.teamId == teamId }
                .mapNotNull { submission ->
                    val parentJson = JSONObject(submission.parent ?: "{}")
                    parentJson.optString("_id")
                }
                .filter { it.isNotEmpty() }
                .toSet()

            query.beginGroup()
                .equalTo("teamId", teamId)
                .or()
                .`in`("id", teamSpecificExams.toTypedArray())
            query.endGroup()
        }
        if(etSearch.text.toString().isNotEmpty()){
            val surveys = query.findAll()
            adapter.updateData(safeCastList(search(etSearch.text.toString(), surveys), RealmStepExam::class.java))
        } else {
            val surveys = query.sort(field, sort).findAll()
            adapter.updateData(safeCastList(surveys, RealmStepExam::class.java))
        }

        updateUIState()
        recyclerView.scrollToPosition(0)
    }

    private fun updateUIState() {
        val itemCount = adapter.itemCount
        spn.visibility = if (itemCount == 0) View.GONE else View.VISIBLE
        showNoData(tvMessage, itemCount, "survey")
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        val sort = if (isTitleAscending) Sort.ASCENDING else Sort.DESCENDING
        updateAdapterData(sort, "name", isTeamShareAllowed = false)
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
