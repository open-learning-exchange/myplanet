package org.ole.planet.myplanet.ui.survey

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.utilities.Utilities

class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>() {
    private lateinit var addNewServey: FloatingActionButton
    private lateinit var spn: Spinner
    override fun getLayout(): Int {
        return R.layout.fragment_survey
    }

    override fun getAdapter(): AdapterSurvey? {
        return model.id?.let { AdapterSurvey(requireActivity(), getList(RealmStepExam::class.java, "name", Sort.ASCENDING) as List<RealmStepExam>, mRealm, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spn = requireView().findViewById(R.id.spn_sort)
        addNewServey = requireView().findViewById(R.id.fab_add_new_survey)
        addNewServey.setOnClickListener { }
        if (adapter != null) showNoData(tvMessage, adapter!!.itemCount)
        spn.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                Utilities.log("i $i")
                if (view != null) {
                    if (i == 0) {
                        recyclerView.adapter = activity?.let { model.id?.let { it1 -> AdapterSurvey(it, getList(RealmStepExam::class.java, "name", Sort.ASCENDING) as List<RealmStepExam>, mRealm, it1) } }
                    } else {
                        recyclerView.adapter = activity?.let { model.id?.let { it1 -> AdapterSurvey(it, getList(RealmStepExam::class.java, "name", Sort.DESCENDING) as List<RealmStepExam>, mRealm, it1) } }
                    }
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
    }
}
