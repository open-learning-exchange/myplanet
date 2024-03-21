package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
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
import org.ole.planet.myplanet.utilities.TimeUtils.getFormatedDate
import org.ole.planet.myplanet.utilities.Utilities

class AdapterMySubmission(private val context: Context, private val list: List<RealmSubmission>?, private val examHashMap: HashMap<String?, RealmStepExam>?) : RecyclerView.Adapter<ViewHolderMySurvey>() {
    private lateinit var rowMysurveyBinding: RowMysurveyBinding
    private var listener: OnHomeItemClickListener? = null
    private var type = ""
    private var mRealm: Realm? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        if (list != null && list.isEmpty()) {
            Toast.makeText(SyncObjectServerFacade.getApplicationContext(), context.getString(R.string.no_items), Toast.LENGTH_SHORT).show()
        }
    }

    fun setmRealm(mRealm: Realm?) {
        this.mRealm = mRealm
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMySurvey {
        rowMysurveyBinding = RowMysurveyBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderMySurvey(rowMysurveyBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMySurvey, position: Int) {
        rowMysurveyBinding.status.text = list?.get(position)?.status
        rowMysurveyBinding.date.text = getFormatedDate(list?.get(position)?.startTime)
        showSubmittedBy(rowMysurveyBinding.submittedBy, position)
        if (examHashMap?.containsKey(list?.get(position)?.parentId) == true)
            rowMysurveyBinding.title.text = examHashMap[list?.get(position)?.parentId]?.name
        holder.itemView.setOnClickListener {
            if (type == "survey")
                openSurvey(listener, list?.get(position)?.id, true)
            else
                openSubmissionDetail(listener, list?.get(position)?.id)
        }
    }

    private fun showSubmittedBy(submitted_by: TextView, position: Int) {
        submitted_by.visibility = View.VISIBLE
        try {
            val ob = list?.get(position)?.user?.let { JSONObject(it) }
            if (ob != null) {
                submitted_by.text = ob.optString("name")
            }
        } catch (e: Exception) {
            val user = mRealm?.where(RealmUserModel::class.java)?.equalTo("id", list?.get(position)?.userId)?.findFirst()
            if (user != null) {
                submitted_by.text = user.name
            }
        }
    }

    private fun openSubmissionDetail(listener: OnHomeItemClickListener?, id: String?) {
        if (listener != null) {
            val b = Bundle()
            b.putString("id", id)
            val f: Fragment = SubmissionDetailFragment()
            f.arguments = b
            listener.openCallFragment(f)}
    }

    override fun getItemCount(): Int {
        return list?.size ?: 0
    }

    fun setType(type: String?) {
        if (type != null) {
            this.type = type
        }
    }

    class ViewHolderMySurvey(rowMysurveyBinding: RowMysurveyBinding) : RecyclerView.ViewHolder(rowMysurveyBinding.root)

    companion object {
        @JvmStatic
        fun openSurvey(listener: OnHomeItemClickListener?, id: String?, isMySurvey: Boolean) {
            Utilities.log("EXAM ID $id")
            if (listener != null) {
                val b = Bundle()
                b.putString("type", "survey")
                b.putString("id", id)
                b.putBoolean("isMySurvey", isMySurvey)
                val f: Fragment = TakeExamFragment()
                f.arguments = b
                listener.openCallFragment(f)
            }
        }
    }
}
