package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.realm.Realm
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowSurveyBinding
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getNoOfSubmissionByUser
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getRecentSubmissionDate
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.team.BaseTeamFragment.Companion.settings
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import java.util.UUID

class AdapterSurvey(private val context: Context, private val mRealm: Realm, private val userId: String, private val isTeam: Boolean, val teamId: String?) : RecyclerView.Adapter<AdapterSurvey.ViewHolderSurvey>() {
    private var examList: List<RealmStepExam> = emptyList()
    private var listener: OnHomeItemClickListener? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    fun updateData(newList: List<RealmStepExam>) {
        val diffCallback = SurveyDiffCallback(examList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        examList = newList
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderSurvey {
        val binding = RowSurveyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderSurvey(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderSurvey, position: Int) {
        holder.bind(examList[position])
    }

    override fun getItemCount(): Int = examList.size

    inner class ViewHolderSurvey(private val binding: RowSurveyBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.startSurvey.visibility = View.VISIBLE
            binding.sendSurvey.visibility = View.GONE
            binding.sendSurvey.setOnClickListener {
                val current = examList[bindingAdapterPosition]
                listener?.sendSurvey(current)
            }
        }

        val filteredParentIds = mRealm.where(RealmSubmission::class.java)
            .isNotNull("membershipDoc")
            .findAll()
            .mapNotNull { submission ->
                val parentJson = JSONObject(submission.parent ?: "{}")
                parentJson.optString("_id")
            }
            .filter { it.isNotEmpty() }
            .toSet()

        fun bind(exam: RealmStepExam) {
            binding.apply {
                tvTitle.text = exam.name
                startSurvey.setOnClickListener {
                    when {
                        exam.id !in filteredParentIds && exam.isTeamShareAllowed -> adoptSurvey(exam, teamId)
                        else -> AdapterMySubmission.openSurvey(listener, exam.id, false, isTeam, teamId)
                    }
                }

                val questions = mRealm.where(RealmExamQuestion::class.java)
                    .equalTo("examId", exam.id)
                    .findAll()

                if (questions.isEmpty()) {
                    sendSurvey.visibility = View.GONE
                    startSurvey.visibility = View.GONE
                }

                startSurvey.text = when {
                    exam.id !in filteredParentIds && exam.isTeamShareAllowed -> context.getString(R.string.adopt_survey)
                    exam.isFromNation -> context.getString(R.string.take_survey)
                    else -> context.getString(R.string.record_survey)
                }

                if (userId.startsWith("guest") == true) {
                    startSurvey.visibility = View.GONE
                }

                tvNoSubmissions.text = getNoOfSubmissionByUser(exam.id, exam.courseId, userId, mRealm)
                tvDateCompleted.text = getRecentSubmissionDate(exam.id, exam.courseId, userId, mRealm)
                tvDate.text = formatDate(RealmStepExam.getSurveyCreationTime(exam.id!!, mRealm)!!, "MMM dd, yyyy")
            }
        }

        fun adoptSurvey(exam: RealmStepExam, teamId: String?) {
            val userModel = UserProfileDbHandler(context).userModel
            val sParentCode = settings?.getString("parentCode", "")
            val planetCode = settings?.getString("planetCode", "")

            val parentJsonString = try {
                JSONObject().apply {
                    put("_id", exam.id)
                    put("name", exam.name)
                    put("courseId", exam.courseId ?: "")
                    put("sourcePlanet", exam.sourcePlanet ?: "")
                    put("teamShareAllowed", exam.isTeamShareAllowed)
                    put("noOfQuestions", exam.noOfQuestions)
                    put("isFromNation", exam.isFromNation)
                }.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                "{}"
            }

            val userJsonString = try {
                JSONObject().apply {
                    put("doc", JSONObject().apply {
                        put("_id", userModel?.id)
                        put("name", userModel?.name )
                        put("userId", userModel?.id ?: "")
                        put("teamPlanetCode", planetCode ?: "")
                        put("status", "active")
                        put("type", "team")
                        put("createdBy", userModel?.id ?: "")
                    })

                    if (isTeam && teamId != null) {
                        put("membershipDoc", JSONObject().apply {
                            put("teamId", teamId)
                        })
                    }
                }.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                "{}"
            }

            mRealm.executeTransaction { realm ->
                realm.createObject(RealmSubmission::class.java, UUID.randomUUID().toString()).apply {
                    parentId = exam.id
                    parent = parentJsonString
                    userId = userModel?.id
                    user = userJsonString
                    type = "survey"
                    status = "pending"
                    uploaded = false
                    source = planetCode ?: ""
                    parentCode = sParentCode ?: ""
                    startTime = System.currentTimeMillis()
                    lastUpdateTime = System.currentTimeMillis()
                    isUpdated = true

                    if (isTeam && teamId != null) {
                        membershipDoc = realm.createObject(RealmMembershipDoc::class.java).apply {
                            this.teamId = teamId
                        }
                    }
                }
            }

            // Show success message
            Snackbar.make(binding.root, "Survey adopted successfully!", Snackbar.LENGTH_LONG).show()

            // Refresh the adapter after survey adoption
            (context as? SurveyFragment)?.updateAdapterData(isTeamShareAllowed = false)
        }
    }
}

class SurveyDiffCallback(private val oldList: List<RealmStepExam>, private val newList: List<RealmStepExam>) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

//adoptedOnMyPlanet = true
//RealmSubmission = proxy[
//{id:cddb0897-ffa8-485a-9354-318c54d0b119},
//{_id:null},{_rev:null},
//{parentId:9f7aea540abb0385c470a61f8101c359},
//{type:survey},
//{userId:org.couchdb.user:okuro},
//{user:
//    {"doc":
//        {"_id":"org.couchdb.user:okuro","name":"okuro","userId":"org.couchdb.user:okuro",
//            "teamPlanetCode":"okuro","status":"active","type":"team","createdBy":"org.couchdb.user:okuro"
//        },"membershipDoc":{"teamId":"7757207164460f16eb315b7990031d71"}
//    }
//},
//{startTime:1738844986776},
//{lastUpdateTime:1738844986776},
//{answers:RealmList<RealmAnswer>[0]},
//{grade:0},
//{status:pending},
//{uploaded:false},
//{sender:null},
//{source:okuro},
//{ parentCode:vi},
//{ parent:{
//    "_id":"9f7aea540abb0385c470a61f8101c359","name":"Adoptable survey 2","courseId":"",
//    "sourcePlanet":"okuro","teamShareAllowed":true,"noOfQuestions":1,"isFromNation":false
//}},
//{membershipDoc:null}]


//adoptedFromPlanet
//RealmSubmission = proxy[
//{id:9f7aea540abb0385c470a61f8101e257},
//{_id:9f7aea540abb0385c470a61f8101e257},
//{_rev:1-8f4ee944d93e692d1ae830c3a00e676a},
//{parentId:9f7aea540abb0385c470a61f8101befa},
//{type:survey},
//{userId:},
//{user:
//    {"doc":
//        {"_id":"7757207164460f16eb315b7990031d71","_rev":"1-d41223886766a8aed10ded5907f672ea",
//            "name":"new team","userId":"org.couchdb.user:okuro","limit":0,"amount":0,"date":0,
//            "public":false,"isLeader":false,"createdDate":1732197504659,
//            "description":"new team ha no plan","beginningBalance":0,"sales":0,"otherIncome":0,
//            "wages":0,"otherExpenses":0,"startDate":0,"endDate":0,"updatedDate":0,"teamType":"local",
//            "teamPlanetCode":"okuro","status":"active","parentCode":"learning","type":"team",
//            "createdBy":"org.couchdb.user:okuro"
//        },"membershipDoc":{"teamId":"7757207164460f16eb315b7990031d71"}
//    }
//},
//{startTime:1738778063000},
//{lastUpdateTime:1738778063000},
//{answers:RealmList<RealmAnswer>[0]},
//{grade:0},
//{status:pending},
//{uploaded:false},
//{sender:okuro},
//{source:okuro},
//{parentCode:vi},
//{parent:{
//    "_id":"9f7aea540abb0385c470a61f8101befa","_rev":"1-296fc0c018ce04462a249f6bd4029019",
//    "createdDate":1738777793000,"createdBy":"admin","name":"Adoptable survey 1",
//    "passingPercentage":100,
//    "questions":[{"body":"Is this adoptable 1?","type":"input","correctChoice":"",
//    "marks":1,"choices":[]}],"type":"surveys","teamShareAllowed":true,"updatedDate":1738777793000,
//    "sourcePlanet":"okuro","teamIds":[],"taken":0}},
//{membershipDoc:RealmMembershipDoc}]
//
