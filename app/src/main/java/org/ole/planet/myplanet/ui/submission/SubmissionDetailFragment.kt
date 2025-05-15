package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Realm
import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding
import org.ole.planet.myplanet.model.RealmSubmission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubmissionDetailFragment : Fragment() {
    private lateinit var fragmentSubmissionDetailBinding: FragmentSubmissionDetailBinding
    private var submissionId: String? = null
    private var submission: RealmSubmission? = null
    private lateinit var mRealm: Realm

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentSubmissionDetailBinding = FragmentSubmissionDetailBinding.inflate(inflater, container, false)
        return fragmentSubmissionDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        submissionId = arguments?.getString("id")
        mRealm = Realm.getDefaultInstance()

        submission = loadSubmission()
        Log.d("SubmissionDetailFragment", "Submission ID: $submission")
//        submission?.let {
//            setupHeaderInfo(it)
//            setupRecyclerView(it)
//        }
    }

    private fun loadSubmission(): RealmSubmission? {
        submissionId?.let { id ->
            return mRealm.where(RealmSubmission::class.java)
                .equalTo("id", id)
                .findFirst()
        }
        return null
    }


    override fun onDestroyView() {
        super.onDestroyView()
        if (::mRealm.isInitialized) {
            mRealm.close()
        }
    }
}
