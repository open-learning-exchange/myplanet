package org.ole.planet.myplanet.ui.survey

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.CustomSpinner

class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>() {
    private lateinit var addNewSurvey: FloatingActionButton
    private lateinit var spn: CustomSpinner
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
        adapter = AdapterSurvey(requireActivity(), mRealm, model?.id ?: "", isTeam, teamId)
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        return adapter
    }

    @SuppressLint("ResourceAsColor")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupListeners()
        updateAdapterData(isTeamShareAllowed = false)
        showHideRadioButton()

        val submissions = mRealm.where(RealmSubmission::class.java)
            .findAll()

        for (submission in submissions) {
            Log.d("okuro", "Submission ID: ${submission._id}")
            Log.d("okuro", "User ID: ${submission.userId}")
            Log.d("okuro", "Status: ${submission.status}")

            // Fetch membershipDoc if it exists
            val membershipDoc = submission.membershipDoc
            if (membershipDoc != null) {
                Log.d("okuro", "Membership Team ID: ${membershipDoc.teamId}")
            } else {
                Log.d("okuro", "No membershipDoc found for this submission")
            }
        }
    }

    private fun showHideRadioButton() {
        if (isTeam) {
            rgSurvey.visibility = View.VISIBLE
            rbTeamSurvey.isChecked = true
        }
    }

    private fun initializeViews(view: View) {
        spn = view.findViewById(R.id.spn_sort)
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
                    0 -> updateAdapterData(Sort.ASCENDING, "createdDate", isTeamShareAllowed = false)
                    1 -> updateAdapterData(Sort.DESCENDING, "createdDate", isTeamShareAllowed = false)
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

        rbAdoptSurvey.setOnClickListener {
            updateAdapterData(isTeamShareAllowed = true)
        }

        rbTeamSurvey.setOnClickListener {
            updateAdapterData(isTeamShareAllowed = false)
        }
    }

    private fun updateAdapterData(sort: Sort = Sort.ASCENDING, field: String = "name", isTeamShareAllowed: Boolean) {
        val query = mRealm.where(RealmStepExam::class.java)

        if (isTeamShareAllowed) {
            query.equalTo("isTeamShareAllowed", true)
        } else if (!teamId.isNullOrEmpty() && isTeam) {
            query.equalTo("teamId", teamId)
        }

        val surveys = query.sort(field, sort).findAll()
        Log.d("SurveyFragment", "updateAdapterData: $surveys")
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
