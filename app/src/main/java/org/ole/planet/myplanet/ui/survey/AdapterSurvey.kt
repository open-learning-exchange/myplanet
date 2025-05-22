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
import org.ole.planet.myplanet.callback.SurveyAdoptListener
import org.ole.planet.myplanet.databinding.RowSurveyBinding
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getNoOfSubmissionByTeam
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getNoOfSubmissionByUser
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getRecentSubmissionDate
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.team.BaseTeamFragment.Companion.settings
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import java.util.Collections
import java.util.UUID

class AdapterSurvey(private val context: Context, private val mRealm: Realm, private val userId: String?, private val isTeam: Boolean, val teamId: String?, private val surveyAdoptListener: SurveyAdoptListener) : RecyclerView.Adapter<AdapterSurvey.ViewHolderSurvey>() {
    private var examList: List<RealmStepExam> = emptyList()
    private var listener: OnHomeItemClickListener? = null
    private val adoptedSurveyIds = mutableSetOf<String>()
    private var isTitleAscending = true
    private var activeFilter = 0

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

    fun updateDataAfterSearch(newList: List<RealmStepExam>) {
        if(examList.isEmpty()){
            examList = newList
        } else{
            if(activeFilter == 0){
                SortSurveyList(false, newList)
            } else if(activeFilter == 1) {
                SortSurveyList(true, newList)
            } else{
                SortSurveyListByName(isTitleAscending, newList)
            }
        }
        notifyDataSetChanged()
    }

    private fun SortSurveyList(isAscend: Boolean, list: List<RealmStepExam> = examList){
        val list = list.toList()
        Collections.sort(list) { survey1, survey2 ->
            if (isAscend) {
                survey1?.createdDate!!.compareTo(survey2?.createdDate!!)
            } else {
                survey2?.createdDate!!.compareTo(survey1?.createdDate!!)
            }
        }
        examList = list
    }

    fun SortByDate(isAscend: Boolean){
        activeFilter = if (isAscend) 1 else 0
        SortSurveyList(isAscend)
        notifyDataSetChanged()
    }

    private fun SortSurveyListByName(isAscend: Boolean, list: List<RealmStepExam> = examList){
        examList = if (isAscend) {
            list.sortedBy { it.name?.lowercase() }
        } else {
            list.sortedByDescending { it.name?.lowercase() }
        }
    }

    fun toggleTitleSortOrder() {
        activeFilter = 2
        isTitleAscending = !isTitleAscending
        SortSurveyListByName(isTitleAscending)
        notifyDataSetChanged()
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

        fun bind(exam: RealmStepExam) {
            binding.apply {
                val teamSubmissions = mRealm.where(RealmSubmission::class.java)
                    .equalTo("parentId", "32dfda6f117ffca1403f2192d275064b")
                    .equalTo("membershipDoc.teamId", teamId)
                    .findAll()

                logLargeString("okuro", "teamSubmission: $teamSubmissions")

                Log.d("TAG", "teamId: ${teamSubmissions.size}")

                startSurvey.visibility = View.VISIBLE
                tvTitle.text = exam.name
                if (exam.description?.isNotEmpty() == true) {
                    tvDescription.visibility = View.VISIBLE
                    tvDescription.text = exam.description
                }
                startSurvey.setOnClickListener {
                    val isTeamSubmission = mRealm.where(RealmSubmission::class.java)
                        .equalTo("parentId", exam.id).equalTo("membershipDoc.teamId", teamId)
                        .findFirst() != null

                    val shouldAdopt = exam.isTeamShareAllowed && !isTeamSubmission

                    if (shouldAdopt) {
                        adoptSurvey(exam, teamId)
                    } else {
                        AdapterMySubmission.openSurvey(listener, exam.id, false, isTeam, teamId)
                    }
                }

                val questions = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", exam.id)
                    .findAll()

                if (questions.isEmpty()) {
                    sendSurvey.visibility = View.GONE
                    startSurvey.visibility = View.GONE
                }

                val isTeamSubmission = mRealm.where(RealmSubmission::class.java)
                    .equalTo("parentId", exam.id).equalTo("membershipDoc.teamId", teamId)
                    .findFirst() != null

                val shouldShowAdopt = exam.isTeamShareAllowed && !isTeamSubmission

                startSurvey.text = when {
                    shouldShowAdopt -> context.getString(R.string.adopt_survey)
                    exam.isFromNation -> context.getString(R.string.take_survey)
                    else -> context.getString(R.string.record_survey)
                }

                if (userId?.startsWith("guest") == true) {
                    startSurvey.visibility = View.GONE
                }

                Log.d("okuro", "teamSubmissions: ${getNoOfSubmissionByTeam(exam.id, exam.courseId, teamId, mRealm)}")
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
                        put("name", userModel?.name)
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

            val adoptionId = "${UUID.randomUUID()}"

            mRealm.executeTransaction { realm ->
                val existingAdoption = realm.where(RealmSubmission::class.java)
                    .equalTo("userId", userModel?.id)
                    .equalTo("parentId", exam.id)
                    .equalTo("status", "")
                    .findFirst()

                if (existingAdoption == null) {
                    realm.createObject(RealmSubmission::class.java, adoptionId).apply {
                        parentId = exam.id
                        parent = parentJsonString
                        userId = userModel?.id
                        user = userJsonString
                        type = "survey"
                        status = ""
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
            }

            adoptedSurveyIds.add("${exam.id}")

            val position = examList.indexOfFirst { it.id == exam.id }
            if (position != -1) {
                notifyItemChanged(position)
            }

            Snackbar.make(binding.root, context.getString(R.string.survey_adopted_successfully), Snackbar.LENGTH_LONG).show()
            surveyAdoptListener.onSurveyAdopted()
        }

        fun logLargeString(tag: String, content: String) {
            if (content.length > 3000) {
                Log.d(tag, content.substring(0, 3000))
                logLargeString(tag, content.substring(3000))
            } else {
                Log.d(tag, content)
            }
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
