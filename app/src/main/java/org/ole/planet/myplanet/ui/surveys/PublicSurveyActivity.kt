package org.ole.planet.myplanet.ui.surveys

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityPublicSurveyBinding
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.exam.ExamTakingFragment
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils

@AndroidEntryPoint
class PublicSurveyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPublicSurveyBinding
    private var backStackListener: FragmentManager.OnBackStackChangedListener? = null

    private val viewModel: PublicSurveyViewModel by viewModels()

    @Inject
    lateinit var prefData: SharedPrefManager

    private var baseUrl = ""
    private var teamId = ""
    private var surveyId = ""

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

        observeViewModel()

        supportFragmentManager.registerFragmentLifecycleCallbacks(userInfoDialogCallback, true)
        backStackListener = FragmentManager.OnBackStackChangedListener {
            if (viewModel.surveyStarted && supportFragmentManager.backStackEntryCount == 0 && !isFinishing) {
                viewModel.uploadCompletedSubmission(baseUrl, teamId, surveyId)
            }
        }
        backStackListener?.let { supportFragmentManager.addOnBackStackChangedListener(it) }

        viewModel.loadSurvey(baseUrl, teamId, surveyId)
    }

    private val userInfoDialogCallback = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
            if (f is UserInformationFragment && viewModel.surveyStarted && !isFinishing) {
                viewModel.uploadCompletedSubmission(baseUrl, teamId, surveyId)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is PublicSurveyEvent.SurveyLoaded -> {
                        showExamTakingFragment(event.surveyId, event.teamId)
                    }
                    is PublicSurveyEvent.SurveyLoadFailed -> {
                        Toast.makeText(this@PublicSurveyActivity, R.string.survey_load_failed, Toast.LENGTH_LONG).show()
                        finish()
                    }
                    is PublicSurveyEvent.UploadFinished -> {
                        Toast.makeText(
                            this@PublicSurveyActivity,
                            if (event.success) R.string.survey_submitted else R.string.survey_submit_failed,
                            Toast.LENGTH_LONG,
                        ).show()
                        navigateOnwardAndFinish()
                    }
                    is PublicSurveyEvent.NavigateOnward -> {
                        navigateOnwardAndFinish()
                    }
                }
            }
        }
    }

    private fun showExamTakingFragment(surveyId: String, teamId: String) {
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

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(userInfoDialogCallback)
        backStackListener?.let { supportFragmentManager.removeOnBackStackChangedListener(it) }
        super.onDestroy()
    }

    companion object {
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
