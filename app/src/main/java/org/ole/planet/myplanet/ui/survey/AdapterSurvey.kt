package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
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
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission

class AdapterSurvey(
    private val context: Context,
    private val mRealm: Realm,
    private val userId: String?,
    private val isTeam: Boolean,
    val teamId: String?,
    private val surveyAdoptListener: SurveyAdoptListener,
    private val settings: SharedPreferences,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val surveyInfoMap: Map<String, SurveyInfo>,
    private val questionsMap: Map<String, List<RealmExamQuestion>>,
    private val submissionsMap: Map<String, RealmSubmission?>
) : ListAdapter<RealmStepExam, AdapterSurvey.ViewHolderSurvey>(SurveyDiffCallback()) {
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

    fun updateData(newList: List<RealmStepExam>, onComplete: (() -> Unit)? = null) {
        submitList(newList) {
            onComplete?.invoke()
        }
    }

    fun updateDataAfterSearch(newList: List<RealmStepExam>, onComplete: (() -> Unit)? = null) {
        val sortedList = if (currentList.isEmpty()) {
            sortSurveyList(false, newList)
        } else {
            sortStrategy(newList)
        }
        submitList(sortedList) {
            onComplete?.invoke()
        }
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
        sortStrategy = { list -> sortSurveyList(isAscend, list) }
        val sortedList = sortStrategy(currentList)
        submitList(sortedList)
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
        sortStrategy = { list -> sortSurveyListByName(isTitleAscending, list) }
        val sortedList = sortStrategy(currentList)
        submitList(sortedList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderSurvey {
        val binding = RowSurveyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderSurvey(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderSurvey, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolderSurvey(private val binding: RowSurveyBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.startSurvey.visibility = View.VISIBLE
            binding.sendSurvey.visibility = View.GONE
            binding.sendSurvey.setOnClickListener {
                val current = getItem(bindingAdapterPosition)
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

                val teamSubmission = submissionsMap[exam.id]
                startSurvey.setOnClickListener {
                    val shouldAdopt = exam.isTeamShareAllowed && teamSubmission?.isValid != true
                    if (shouldAdopt) {
                        adoptSurvey(exam, teamId)
                    } else {
                        AdapterMySubmission.openSurvey(listener, exam.id, false, isTeam, teamId)
                    }
                }

                val questions = questionsMap[exam.id] ?: emptyList()

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
                    val teamName = if (isTeam && teamId != null) {
                        realm.where(RealmMyTeam::class.java)
                            .equalTo("_id", teamId)
                            .findFirst()?.name
                    } else null

                    if (isTeam && teamId != null && teamName != null) {
                        val newSurveyId = UUID.randomUUID().toString()

                        val existingSurvey = realm.where(RealmStepExam::class.java)
                            .equalTo("sourceSurveyId", examId)
                            .equalTo("teamId", teamId)
                            .findFirst()

                        if (existingSurvey == null) {
                            realm.createObject(RealmStepExam::class.java, newSurveyId).apply {
                                _rev = null
                                createdDate = System.currentTimeMillis()
                                updatedDate = System.currentTimeMillis()
                                createdBy = userModel?.id
                                totalMarks = exam.totalMarks
                                name = "${exam.name} - $teamName"
                                description = exam.description
                                type = exam.type
                                stepId = exam.stepId
                                courseId = exam.courseId
                                sourcePlanet = exam.sourcePlanet
                                passingPercentage = exam.passingPercentage
                                noOfQuestions = exam.noOfQuestions
                                isFromNation = exam.isFromNation

                                this.teamId = teamId
                                sourceSurveyId = examId
                                isTeamShareAllowed = false
                            }

                            val questions = realm.where(RealmExamQuestion::class.java)
                                .equalTo("examId", examId)
                                .findAll()

                            val questionsArray = RealmExamQuestion.serializeQuestions(questions)
                            RealmExamQuestion.insertExamQuestions(questionsArray, newSurveyId, realm)
                        }
                    }

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
                                val team = realm.where(RealmMyTeam::class.java)
                                    .equalTo("_id", teamId)
                                    .findFirst()

                                if (team != null) {
                                    val teamRef = realm.createObject(org.ole.planet.myplanet.model.RealmTeamReference::class.java)
                                    teamRef._id = team._id
                                    teamRef.name = team.name
                                    teamRef.type = team.type ?: "team"
                                    teamObject = teamRef
                                }

                                membershipDoc = realm.createObject(RealmMembershipDoc::class.java).apply {
                                    this.teamId = teamId
                                }
                            }
                        }
                    }
                }, {
                    mRealm.refresh()
                    adoptedSurveyIds.add("$examId")
                    val position = currentList.indexOfFirst { it.id == examId }
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

class SurveyDiffCallback : DiffUtil.ItemCallback<RealmStepExam>() {
    override fun areItemsTheSame(oldItem: RealmStepExam, newItem: RealmStepExam): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: RealmStepExam, newItem: RealmStepExam): Boolean {
        return oldItem == newItem
    }
}
