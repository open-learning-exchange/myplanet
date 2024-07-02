package org.ole.planet.myplanet.ui.survey

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.model.RealmStepExam

class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>() {
    private lateinit var addNewServey: FloatingActionButton
    private lateinit var spn: Spinner
    override fun getLayout(): Int {
        return R.layout.fragment_survey
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        return model?.id?.let {
            AdapterSurvey(requireActivity(), safeCastList(getList(RealmStepExam::class.java, "name", Sort.ASCENDING), RealmStepExam::class.java), mRealm, it)
        }!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spn = requireView().findViewById(R.id.spn_sort)
        addNewServey = requireView().findViewById(R.id.fab_add_new_survey)
        addNewServey.setOnClickListener { }
        if (getAdapter().itemCount == 0) {
            spn.visibility = View.GONE
        }
        showNoData(tvMessage, getAdapter().itemCount, "survey")
        spn.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (view != null) {
                    if (i == 0) {
                        recyclerView.adapter = activity?.let { act -> model?.id?.let { id -> AdapterSurvey(act, safeCastList(getList(RealmStepExam::class.java, "name", Sort.ASCENDING), RealmStepExam::class.java), mRealm, id) } }
                    } else {
                        recyclerView.adapter = activity?.let { act -> model?.id?.let { id -> AdapterSurvey(act, safeCastList(getList(RealmStepExam::class.java, "name", Sort.DESCENDING), RealmStepExam::class.java), mRealm, id) } }
                    }
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
    }

    fun <T> safeCastList(items: List<Any?>, clazz: Class<T>): List<T> {
        return items.mapNotNull { it?.takeIf(clazz::isInstance)?.let(clazz::cast) }
    }
}
