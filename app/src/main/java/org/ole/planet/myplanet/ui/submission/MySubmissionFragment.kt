package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.databinding.FragmentMySubmissionBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmStepExam.Companion.getIds
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getExamMap
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler

class MySubmissionFragment : Fragment(), CompoundButton.OnCheckedChangeListener {
    private lateinit var fragmentMySubmissionBinding: FragmentMySubmissionBinding
    lateinit var mRealm: Realm
    var type: String? = ""
    var exams: HashMap<String?, RealmStepExam>? = null
    private var submissions: List<RealmSubmission>? = null
    var user: RealmUserModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) type = requireArguments().getString("type")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMySubmissionBinding = FragmentMySubmissionBinding.inflate(inflater, container, false)
        exams = HashMap()
        user = UserProfileDbHandler(requireContext()).userModel
        return fragmentMySubmissionBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRealm = DatabaseService(requireActivity()).realmInstance
        fragmentMySubmissionBinding.rvMysurvey.layoutManager = LinearLayoutManager(activity)
        fragmentMySubmissionBinding.rvMysurvey.addItemDecoration(
            DividerItemDecoration(activity, DividerItemDecoration.VERTICAL)
        )
        submissions = mRealm.where(RealmSubmission::class.java).findAll()
        exams = getExamMap(mRealm, submissions)
        setData("")
        fragmentMySubmissionBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                val cleanString = charSequence.toString()
                setData(cleanString)
            }
            override fun afterTextChanged(editable: Editable) {}
        })
        showHideRadioButton()
    }

    private fun showHideRadioButton() {
        if (type != "survey") {
            fragmentMySubmissionBinding.rbExam.isChecked = true
            fragmentMySubmissionBinding.rbExam.setOnCheckedChangeListener(this)
            fragmentMySubmissionBinding.rbSurvey.setOnCheckedChangeListener(this)
        } else {
            fragmentMySubmissionBinding.rbSurvey.visibility = View.GONE
            fragmentMySubmissionBinding.rbExam.visibility = View.GONE
            fragmentMySubmissionBinding.rgSubmission.visibility = View.GONE
        }
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        type = if (fragmentMySubmissionBinding.rbSurvey.isChecked) {
            "survey_submission"
        } else {
            "exam"
        }
        setData("")
    }

    private fun setData(s: String) {
        val q: RealmQuery<*>? = when (type) {
            "survey" -> mRealm.where(RealmSubmission::class.java).equalTo("userId", user?.id)
                .equalTo("type", "survey")
            "survey_submission" -> mRealm.where(RealmSubmission::class.java).equalTo("userId", user?.id)
                .notEqualTo("status", "pending").equalTo("type", "survey")
            else -> mRealm.where(RealmSubmission::class.java).equalTo("userId", user?.id)
                .notEqualTo("type", "survey")
        }
        if (!TextUtils.isEmpty(s)) {
            val ex: List<RealmStepExam> = mRealm.where(RealmStepExam::class.java)
                .contains("name", s, Case.INSENSITIVE).findAll()
            q?.`in`("parentId", getIds(ex))
        }
        if (q != null) {
            submissions = q.findAll().mapNotNull { it as? RealmSubmission }
        }

        val adapter = AdapterMySubmission(requireActivity(), submissions, exams)
        val itemCount = adapter.itemCount

        if (s.isEmpty()) {
            fragmentMySubmissionBinding.llSearch.visibility = View.VISIBLE
            fragmentMySubmissionBinding.title.visibility = View.VISIBLE
            if (fragmentMySubmissionBinding.rbSurvey.isChecked || type == "survey") {
                fragmentMySubmissionBinding.tvFragmentInfo.text = "mySurveys"
                showNoData(fragmentMySubmissionBinding.tvMessage, itemCount, "survey_submission")

            } else {
                fragmentMySubmissionBinding.tvFragmentInfo.text = "mySubmissions"
                showNoData(fragmentMySubmissionBinding.tvMessage, itemCount, "exam_submission")
            }

            if (itemCount == 0) {
                fragmentMySubmissionBinding.title.visibility = View.GONE
                fragmentMySubmissionBinding.tlSearch.visibility = View.GONE
            } else {
                fragmentMySubmissionBinding.tvMessage.visibility = View.GONE
                fragmentMySubmissionBinding.title.visibility = View.VISIBLE
                fragmentMySubmissionBinding.tlSearch.visibility = View.VISIBLE
            }
        }

        adapter.setmRealm(mRealm)
        adapter.setType(type)
        fragmentMySubmissionBinding.rvMysurvey.adapter = adapter
    }

    companion object {
        @JvmStatic
        fun newInstance(type: String?): Fragment {
            val fragment = MySubmissionFragment()
            val b = Bundle()
            b.putString("type", type)
            fragment.arguments = b
            return fragment
        }
    }
}
