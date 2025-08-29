package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import io.realm.internal.SyncObjectServerFacade
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowMysurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission.ViewHolderMySurvey
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class AdapterMySubmission(
    private val context: Context,
    list: List<RealmSubmission>?,
    private val examHashMap: HashMap<String?, RealmStepExam>?
) : ListAdapter<RealmSubmission, ViewHolderMySurvey>(DIFF_CALLBACK) {
    private lateinit var rowMySurveyBinding: RowMysurveyBinding
    private var listener: OnHomeItemClickListener? = null
    private var type = ""
    private var mRealm: Realm? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        if (list != null && list.isEmpty()) {
            Toast.makeText(
                SyncObjectServerFacade.getApplicationContext(),
                context.getString(R.string.no_items),
                Toast.LENGTH_SHORT
            ).show()
        }
        submitList(list)
    }

    fun setmRealm(mRealm: Realm?) {
        this.mRealm = mRealm
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMySurvey {
        rowMySurveyBinding = RowMysurveyBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderMySurvey(rowMySurveyBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMySurvey, position: Int) {
        val submission = getItem(position)
        rowMySurveyBinding.status.text = submission.status
        rowMySurveyBinding.date.text = getFormattedDate(submission.startTime)
        showSubmittedBy(rowMySurveyBinding.submittedBy, submission)
        if (examHashMap?.containsKey(submission.parentId) == true) {
            rowMySurveyBinding.title.text = examHashMap[submission.parentId]?.name
        }
        holder.itemView.setOnClickListener {
            if (type == "survey") {
                openSurvey(listener, submission.id, true, false, "")
            } else {
                openSubmissionDetail(listener, submission.id)
            }
        }
    }

    private fun showSubmittedBy(submittedBy: TextView, submission: RealmSubmission) {
        submittedBy.visibility = View.VISIBLE
        try {
            val ob = submission.user?.let { JSONObject(it) }
            if (ob != null) {
                submittedBy.text = ob.optString("name")
            }
        } catch (e: Exception) {
            val user =
                mRealm?.where(RealmUserModel::class.java)?.equalTo("id", submission.userId)?.findFirst()
            if (user != null) {
                submittedBy.text = user.name
            }
        }
    }

    private fun openSubmissionDetail(listener: OnHomeItemClickListener?, id: String?) {
        if (listener != null) {
            val b = Bundle()
            b.putString("id", id)
            val f: Fragment = SubmissionDetailFragment()
            f.arguments = b
            listener.openCallFragment(f)
        }
    }

    fun setType(type: String?) {
        if (type != null) {
            this.type = type
        }
    }

    class ViewHolderMySurvey(rowMySurveyBinding: RowMysurveyBinding) : RecyclerView.ViewHolder(rowMySurveyBinding.root)

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RealmSubmission>() {
            override fun areItemsTheSame(oldItem: RealmSubmission, newItem: RealmSubmission): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RealmSubmission, newItem: RealmSubmission): Boolean {
                return oldItem.id == newItem.id &&
                    oldItem.status == newItem.status &&
                    oldItem.lastUpdateTime == newItem.lastUpdateTime
            }
        }

        @JvmStatic
        fun openSurvey(listener: OnHomeItemClickListener?, id: String?, isMySurvey: Boolean, isTeam: Boolean, teamId: String?) {
            if (listener != null) {
                val b = Bundle()
                b.putString("type", "survey")
                b.putString("id", id)
                b.putBoolean("isMySurvey", isMySurvey)
                b.putBoolean("isTeam", isTeam)
                b.putString("teamId", teamId)
                val f: Fragment = TakeExamFragment()
                f.arguments = b
                listener.openCallFragment(f)
            }
        }
    }
}
