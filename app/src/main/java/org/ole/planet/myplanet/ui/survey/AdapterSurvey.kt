package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
    private val userProfileDbHandler: UserProfileDbHandler
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

                // Query for existing team submission
                fun getTeamSubmission(): RealmSubmission? {
                    return mRealm.where(RealmSubmission::class.java)
                        .equalTo("parentId", exam.id)
                        .equalTo("membershipDoc.teamId", teamId)
                        .findFirst()
                }

                var teamSubmission = getTeamSubmission()

                startSurvey.setOnClickListener {
                    val clickTime = System.currentTimeMillis()
                    Log.d("SurveyAdoption", "═══════════════════════════════════════")
                    Log.d("SurveyAdoption", "Button clicked at: $clickTime")
                    Log.d("SurveyAdoption", "Survey: ${exam.name} (${exam.id})")

                    // Always re-query to get the latest state
                    teamSubmission = getTeamSubmission()

                    val shouldAdopt = exam.isTeamShareAllowed && teamSubmission?.isValid != true

                    if (shouldAdopt) {
                        Log.d("SurveyAdoption", "Action: Adopting survey")
                        adoptSurvey(exam, teamId, clickTime)
                    } else {
                        Log.d("SurveyAdoption", "Action: Opening existing survey")
                        AdapterMySubmission.openSurvey(listener, exam.id, false, isTeam, teamId)
                    }
                }

                val questions = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", exam.id)
                    .findAll()

                if (questions.isEmpty()) {
                    sendSurvey.visibility = View.GONE
                    startSurvey.visibility = View.GONE
                }

                // Always re-query to ensure button text matches current state
                teamSubmission = getTeamSubmission()
                val shouldShowAdopt = exam.isTeamShareAllowed && teamSubmission?.isValid != true

                startSurvey.text = when {
                    shouldShowAdopt -> context.getString(R.string.adopt_survey)
                    exam.isFromNation -> context.getString(R.string.take_survey)
                    else -> context.getString(R.string.record_survey)
                }

                if (userId?.startsWith("guest") == true) {
                    startSurvey.visibility = View.GONE
                }

                tvNoSubmissions.text = when {
                    isTeam -> getNoOfSubmissionByTeam(teamId, exam.id, mRealm)
                    else -> getNoOfSubmissionByUser(exam.id, exam.courseId, userId, mRealm)
                }
                tvDateCompleted.text = getRecentSubmissionDate(exam.id, exam.courseId, userId, mRealm)
                val creationTime = exam.id?.let { RealmStepExam.getSurveyCreationTime(it, mRealm) }
                tvDate.text = creationTime?.let { formatDate(it, "MMM dd, yyyy") } ?: ""
            }
        }

        fun adoptSurvey(exam: RealmStepExam, teamId: String?, clickTime: Long) {
            val methodStartTime = System.currentTimeMillis()
            Log.d("SurveyAdoption", "adoptSurvey() entered at: $methodStartTime (+${methodStartTime - clickTime}ms from click)")

            val userModel = userProfileDbHandler.userModel
            val sParentCode = settings.getString("parentCode", "")
            val planetCode = settings.getString("planetCode", "")

            val jsonBuildStart = System.currentTimeMillis()
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

            val jsonBuildEnd = System.currentTimeMillis()
            Log.d("SurveyAdoption", "JSON prep completed: $jsonBuildEnd (+${jsonBuildEnd - jsonBuildStart}ms)")

            val adoptionId = "${UUID.randomUUID()}"
            val examId = exam.id
            val userId = userModel?.id

            if (mRealm.isClosed) {
                Log.d("SurveyAdoption", "ERROR: Realm is closed")
                Snackbar.make(binding.root, context.getString(R.string.failed_to_adopt_survey), Snackbar.LENGTH_LONG).show()
                return
            }

            val transactionStartTime = System.currentTimeMillis()
            Log.d("SurveyAdoption", "Starting async transaction: $transactionStartTime (+${transactionStartTime - clickTime}ms total)")

            try {
                mRealm.executeTransactionAsync({ realm ->
                    val txStart = System.currentTimeMillis()
                    Log.d("SurveyAdoption", "[BG Thread] Transaction block entered: $txStart")

                    val queryStart = System.currentTimeMillis()
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

                    val queryEnd = System.currentTimeMillis()
                    Log.d("SurveyAdoption", "[BG Thread] Query completed: $queryEnd (+${queryEnd - queryStart}ms) - Found existing: ${existingAdoption != null}")

                    if (existingAdoption == null) {
                        val createStart = System.currentTimeMillis()
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
                        val createEnd = System.currentTimeMillis()
                        Log.d("SurveyAdoption", "[BG Thread] Object created: $createEnd (+${createEnd - createStart}ms)")
                    }

                    val txEnd = System.currentTimeMillis()
                    Log.d("SurveyAdoption", "[BG Thread] Transaction block completed: $txEnd (total: ${txEnd - txStart}ms)")
                }, {
                    val successCallbackTime = System.currentTimeMillis()
                    Log.d("SurveyAdoption", "[Main Thread] Success callback: $successCallbackTime (+${successCallbackTime - clickTime}ms total)")

                    val refreshStart = System.currentTimeMillis()
                    mRealm.refresh()
                    val refreshEnd = System.currentTimeMillis()
                    Log.d("SurveyAdoption", "Realm refresh completed: $refreshEnd (+${refreshEnd - refreshStart}ms)")

                    adoptedSurveyIds.add("$examId")
                    val position = examList.indexOfFirst { it.id == examId }
                    if (position != -1) {
                        val notifyStart = System.currentTimeMillis()
                        notifyItemChanged(position)
                        val notifyEnd = System.currentTimeMillis()
                        Log.d("SurveyAdoption", "notifyItemChanged at position $position: $notifyEnd (+${notifyEnd - notifyStart}ms)")
                    }

                    val snackbarTime = System.currentTimeMillis()
                    Snackbar.make(binding.root, context.getString(R.string.survey_adopted_successfully), Snackbar.LENGTH_LONG).show()
                    Log.d("SurveyAdoption", "Snackbar shown: $snackbarTime")

                    val callbackStart = System.currentTimeMillis()
                    surveyAdoptListener.onSurveyAdopted()
                    val callbackEnd = System.currentTimeMillis()
                    Log.d("SurveyAdoption", "onSurveyAdopted() callback: $callbackEnd (+${callbackEnd - callbackStart}ms)")
                    Log.d("SurveyAdoption", "TOTAL TIME: ${callbackEnd - clickTime}ms")
                    Log.d("SurveyAdoption", "═══════════════════════════════════════")
                }, { error ->
                    val errorTime = System.currentTimeMillis()
                    Log.e("SurveyAdoption", "ERROR callback: $errorTime - ${error.message}")
                    Log.e("SurveyAdoption", "═══════════════════════════════════════")
                    Snackbar.make(binding.root, context.getString(R.string.failed_to_adopt_survey), Snackbar.LENGTH_LONG).show()
                })
            } catch (e: Exception) {
                val exceptionTime = System.currentTimeMillis()
                Log.e("SurveyAdoption", "EXCEPTION: $exceptionTime - ${e.message}")
                Log.e("SurveyAdoption", "═══════════════════════════════════════")
                Snackbar.make(binding.root, context.getString(R.string.failed_to_adopt_survey), Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
