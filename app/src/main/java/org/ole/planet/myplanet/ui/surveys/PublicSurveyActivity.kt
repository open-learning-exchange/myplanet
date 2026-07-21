package org.ole.planet.myplanet.ui.surveys

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityPublicSurveyBinding
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.exam.ExamTakingFragment
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils

/**
 * Lets anyone respond to a `publicAccess` survey without logging in. Opened from
 * /survey/<teamId>/<surveyId> deep links. Fetches the survey from the server's public
 * API, stores it in Realm, and hosts the standard [ExamTakingFragment] survey form;
 * the completed submission is then posted back through the public API.
 */
@AndroidEntryPoint
class PublicSurveyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPublicSurveyBinding

    @Inject
    lateinit var surveysRepository: SurveysRepository

    @Inject
    lateinit var submissionsRepository: SubmissionsRepository

    @Inject
    lateinit var prefData: SharedPrefManager

    private var baseUrl = ""
    private var teamId = ""
    private var surveyId = ""
    private var surveyStarted = false
    private var launchTime = 0L
    private var uploading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPublicSurveyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)

        baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        teamId = intent.getStringExtra(EXTRA_TEAM_ID).orEmpty()
        surveyId = intent.getStringExtra(EXTRA_SURVEY_ID).orEmpty()
        if (baseUrl.isEmpty() || teamId.isEmpty() || surveyId.isEmpty()) {
            finish()
            return
        }

        // The respondent-info dialog (shown at the end of the survey) closing is our signal
        // to upload the completed submission — regardless of teamId, and without going through
        // the standard authenticated upload path, which anonymous public respondents can't use.
        supportFragmentManager.registerFragmentLifecycleCallbacks(userInfoDialogCallback, true)
        // Backing out of the survey before finishing pops the form off the back stack; close then too.
        supportFragmentManager.addOnBackStackChangedListener {
            if (surveyStarted && supportFragmentManager.backStackEntryCount == 0 && !isFinishing) {
                uploadCompletedSubmission()
            }
        }

        loadSurvey()
    }

    private val userInfoDialogCallback = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
            if (f is UserInformationFragment && surveyStarted && !isFinishing) {
                uploadCompletedSubmission()
            }
        }
    }

    private fun loadSurvey() {
        lifecycleScope.launch {
            val response = surveysRepository.fetchPublicSurvey(baseUrl, teamId, surveyId)
            val surveyDoc = when {
                response == null -> null
                response.has("survey") && response.get("survey").isJsonObject -> response.getAsJsonObject("survey")
                else -> response
            }
            if (surveyDoc == null) {
                Toast.makeText(this@PublicSurveyActivity, R.string.survey_load_failed, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            surveysRepository.saveSurveyFromPublicApi(surveyDoc)

            binding.progressBar.visibility = View.GONE
            launchTime = System.currentTimeMillis()
            surveyStarted = true
            // isTeam=true so the form ends with the team-survey UserInformationFragment
            // dialog collecting the (anonymous) respondent's details
            val fragment = ExamTakingFragment().apply {
                arguments = Bundle().apply {
                    putString("type", "survey")
                    putString("id", surveyId)
                    putBoolean("isMySurvey", false)
                    putBoolean("isTeam", true)
                    putString("teamId", teamId)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun uploadCompletedSubmission() {
        if (uploading) return
        uploading = true
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val submission = submissionsRepository.getLatestSubmissionByParentId(surveyId, "complete")
            Log.d(TAG, "upload: submission=${submission?.id} lastUpdate=${submission?.lastUpdateTime} launchTime=$launchTime")
            if (submission == null || submission.lastUpdateTime < launchTime) {
                // User backed out without finishing the survey
                navigateOnwardAndFinish()
                return@launch
            }
            val answers = buildPublicAnswers(submission)
            val respondent = submission.user?.takeIf { it.isNotBlank() && it != "{}" }?.let {
                try {
                    JsonParser.parseString(it).asJsonObject.also(::sanitizeRespondent)
                } catch (e: Exception) {
                    null
                }
            }
            Log.d(TAG, "upload: POST answers=$answers user=$respondent")
            val success = surveysRepository.submitPublicSurvey(baseUrl, teamId, surveyId, answers, respondent)
            Log.d(TAG, "upload: result success=$success")
            Toast.makeText(
                this@PublicSurveyActivity,
                if (success) R.string.survey_submitted else R.string.survey_submit_failed,
                Toast.LENGTH_LONG,
            ).show()
            navigateOnwardAndFinish()
        }
    }

    // Per product requirement: after the survey ends, send a logged-in user to the dashboard
    // and a logged-out (anonymous) respondent to the login screen.
    private fun navigateOnwardAndFinish() {
        val next = if (prefData.isLoggedIn()) {
            Intent(this, DashboardActivity::class.java)
                .putExtra("from_login", true)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(next)
        finish()
    }

    // The public API validates user.age as an integer, but the stored profile serializes it
    // as a string; coerce it (dropping it if non-numeric) so the submission isn't rejected.
    private fun sanitizeRespondent(user: com.google.gson.JsonObject) {
        if (user.has("age")) {
            val age = user.get("age").asString.trim().toIntOrNull()
            if (age != null) user.addProperty("age", age) else user.remove("age")
        }
    }

    // The public API expects one entry per question: a string for text/rating answers,
    // a {id, text} object for select, and an array of those objects for selectMultiple.
    private suspend fun buildPublicAnswers(submission: RealmSubmission): JsonArray {
        val questions = surveysRepository.getExamQuestions(surveyId)
        val answersByQuestion = submission.answers?.associateBy { it.questionId }.orEmpty()
        val payload = JsonArray()
        questions.forEach { question ->
            val answer = answersByQuestion[question.id]
            val choices = answer?.valueChoicesArray ?: JsonArray()
            when {
                question.type.equals("selectMultiple", ignoreCase = true) -> payload.add(choices)
                question.type.equals("select", ignoreCase = true) && choices.size() > 0 -> payload.add(choices[0])
                else -> payload.add(JsonPrimitive(answer?.value.orEmpty()))
            }
        }
        return payload
    }

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(userInfoDialogCallback)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PublicSurvey"
        private const val EXTRA_BASE_URL = "base_url"
        private const val EXTRA_TEAM_ID = "team_id"
        private const val EXTRA_SURVEY_ID = "survey_id"

        fun newIntent(context: Context, baseUrl: String, teamId: String, surveyId: String): Intent {
            return Intent(context, PublicSurveyActivity::class.java)
                .putExtra(EXTRA_BASE_URL, baseUrl)
                .putExtra(EXTRA_TEAM_ID, teamId)
                .putExtra(EXTRA_SURVEY_ID, surveyId)
        }
    }
}
