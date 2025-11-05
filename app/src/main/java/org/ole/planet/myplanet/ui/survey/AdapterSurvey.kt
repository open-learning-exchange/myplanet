package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.realm.Realm
import java.util.UUID
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
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterSurvey(
    private val context: Context,
    private val mRealm: Realm,
    private val userId: String?,
    private val isTeam: Boolean,
    val teamId: String?,
    private val surveyAdoptListener: SurveyAdoptListener,
    private val settings: SharedPreferences,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val surveyInfoMap: Map<String, SurveyInfo>
) : RecyclerView.Adapter<AdapterSurvey.ViewHolderSurvey>() {
    private var examList: List<RealmStepExam> = emptyList()
    private var listener: OnHomeItemClickListener? = null
    private val adoptedSurveyIds = mutableSetOf<String>()
    private var isTitleAscending = true
    private var sortStrategy: (List<RealmStepExam>) -> List<RealmStepExam> = { list ->
        sortSurveyList(false, list)
    }

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    fun updateData(newList: List<RealmStepExam>) {
        dispatchDiff(examList, newList)
    }

    fun updateDataAfterSearch(newList: List<RealmStepExam>) {
        val oldList = examList
        val sortedList = if (oldList.isEmpty()) {
            sortSurveyList(false, newList)
        } else {
            sortStrategy(newList)
        }
        dispatchDiff(oldList, sortedList)
    }

    private fun sortSurveyList(
        isAscend: Boolean,
        list: List<RealmStepExam>
    ): List<RealmStepExam> {
        return if (isAscend) {
            list.sortedBy { it.createdDate }
        } else {
            list.sortedByDescending { it.createdDate }
        }
    }

    fun sortByDate(isAscend: Boolean) {
        val oldList = examList
        sortStrategy = { list -> sortSurveyList(isAscend, list) }
        val sortedList = sortStrategy(examList)
        dispatchDiff(oldList, sortedList)
    }

    private fun sortSurveyListByName(
        isAscend: Boolean,
        list: List<RealmStepExam>
    ): List<RealmStepExam> {
        return if (isAscend) {
            list.sortedBy { it.name?.lowercase() }
        } else {
            list.sortedByDescending { it.name?.lowercase() }
        }
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        val oldList = examList
        sortStrategy = { list -> sortSurveyListByName(isTitleAscending, list) }
        val sortedList = sortStrategy(examList)
        dispatchDiff(oldList, sortedList)
    }

    private fun dispatchDiff(
        oldList: List<RealmStepExam>,
        newList: List<RealmStepExam>
    ) {
        val diffResult = DiffUtils.calculateDiff(
            oldList,
            newList,
            areItemsTheSame = { old, new -> old.id == new.id },
            areContentsTheSame = { old, new -> old == new }
        )
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

        fun bind(exam: RealmStepExam) {
            binding.apply {
                startSurvey.visibility = View.VISIBLE
                tvTitle.text = exam.name
                if (exam.description?.isNotEmpty() == true) {
                    tvDescription.visibility = View.VISIBLE
                    tvDescription.text = exam.description
                }
                var teamSubmission = mRealm.where(RealmSubmission::class.java)
                    .equalTo("parentId", exam.id)
                    .equalTo("membershipDoc.teamId", teamId)
                    .findFirst()

                startSurvey.setOnClickListener {
                    if (teamSubmission?.isValid != true) {
                        teamSubmission = mRealm.where(RealmSubmission::class.java)
                            .equalTo("parentId", exam.id)
                            .equalTo("membershipDoc.teamId", teamId)
                            .findFirst()
                    }

                    val shouldAdopt = exam.isTeamShareAllowed && teamSubmission?.isValid != true

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

                val shouldShowAdopt = exam.isTeamShareAllowed && teamSubmission?.isValid != true

                startSurvey.text = when {
                    shouldShowAdopt -> context.getString(R.string.adopt_survey)
                    exam.isFromNation -> context.getString(R.string.take_survey)
                    else -> context.getString(R.string.record_survey)
                }

                if (userId?.startsWith("guest") == true) {
                    startSurvey.visibility = View.GONE
                }

                val surveyInfo = surveyInfoMap[exam.id]
                tvNoSubmissions.text = surveyInfo?.submissionCount ?: ""
                tvDateCompleted.text = surveyInfo?.lastSubmissionDate ?: ""
                tvDate.text = surveyInfo?.creationDate ?: ""
            }
        }

        fun adoptSurvey(exam: RealmStepExam, teamId: String?) {
            val userModel = userProfileDbHandler.userModel
            val sParentCode = settings.getString("parentCode", "")
            val planetCode = settings.getString("planetCode", "")

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
            val examId = exam.id
            val userId = userModel?.id

            if (mRealm.isClosed) {
                Snackbar.make(binding.root, context.getString(R.string.failed_to_adopt_survey), Snackbar.LENGTH_LONG).show()
                return
            }

            try {
                mRealm.executeTransactionAsync({ realm ->
                    val existingAdoption = if (isTeam && teamId != null) {
                        realm.where(RealmSubmission::class.java)
                            .equalTo("userId", userId)
                            .equalTo("parentId", examId)
                            .equalTo("status", "")
                            .equalTo("membershipDoc.teamId", teamId)
                            .findFirst()
                    } else {
                        realm.where(RealmSubmission::class.java)
                            .equalTo("userId", userId)
                            .equalTo("parentId", examId)
                            .equalTo("status", "")
                            .isNull("membershipDoc")
                            .findFirst()
                    }

                    if (existingAdoption == null) {
                        realm.createObject(RealmSubmission::class.java, adoptionId).apply {
                            parentId = examId
                            parent = parentJsonString
                            this.userId = userId
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
                }, {
                    mRealm.refresh()

                    adoptedSurveyIds.add("$examId")
                    val position = examList.indexOfFirst { it.id == examId }
                    if (position != -1) {
                        notifyItemChanged(position)
                    }

                    Snackbar.make(binding.root, context.getString(R.string.survey_adopted_successfully), Snackbar.LENGTH_LONG).show()
                    surveyAdoptListener.onSurveyAdopted()
                }, { error ->
                    Snackbar.make(binding.root, context.getString(R.string.failed_to_adopt_survey), Snackbar.LENGTH_LONG).show()
                })
            } catch (e: Exception) {
                Snackbar.make(binding.root, context.getString(R.string.failed_to_adopt_survey), Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
