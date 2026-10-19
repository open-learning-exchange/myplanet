package org.ole.planet.myplanet.base

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.editor.MarkwonEditor
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.exam.ExamViewModel
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.surveys.SurveyFragment
import org.ole.planet.myplanet.utils.CameraUtils
import org.ole.planet.myplanet.utils.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utils.MarkdownPreRenderTextWatcher
import org.ole.planet.myplanet.utils.MarkdownUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
abstract class BaseExamFragment : Fragment(), ImageCaptureCallback {
    var exam: StepExam? = null
    private val viewModel: ExamViewModel by viewModels()
    var stepId: String? = null
    var id: String? = ""
    var type: String? = "exam"
    var currentIndex = 0
    private var stepNumber = 0
    var questions: List<ExamQuestion>? = null
    var ans = ""
    var user: UserEntity? = null
    var sub: Submission? = null
    var listAns: HashMap<String, String>? = null
    var isMySurvey = false
    var date = Date().toString()
    private var photoPath: String? = ""
    var submitId = ""
    var isTeam: Boolean = false
    var teamId: String? = null
    private var answerTextWatcher: MarkdownPreRenderTextWatcher? = null
    private var currentAnswerEditText: EditText? = null
    private var markwonExecutor: ExecutorService? = null
    private val markwon by lazy(LazyThreadSafetyMode.NONE) { MarkdownUtils.create(requireActivity()) }
    private val markwonEditor by lazy(LazyThreadSafetyMode.NONE) { MarkwonEditor.create(markwon) }

    private fun getMarkwonExecutor(): ExecutorService {
        val executor = markwonExecutor
        if (executor == null || executor.isShutdown) {
            val newExecutor = Executors.newSingleThreadExecutor()
            markwonExecutor = newExecutor
            return newExecutor
        }
        return executor
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            stepId = requireArguments().getString("stepId")
            stepNumber = requireArguments().getInt("stepNum")
            isMySurvey = requireArguments().getBoolean("isMySurvey")
            isTeam = requireArguments().getBoolean("isTeam", false)
            teamId = requireArguments().getString("teamId")
            checkType()
        }
    }

    private suspend fun checkId() {
        if (TextUtils.isEmpty(stepId)) {
            id = requireArguments().getString("id")
            if (isMySurvey) {
                id?.let {
                    sub = viewModel.getSubmissionById(it)
                }
                id = sub?.parentId?.substringBefore("@")
            }
        }
    }

    private fun checkType() {
        if (requireArguments().containsKey("type")) {
            type = requireArguments().getString("type")
        }
    }

    suspend fun initExam() {
        checkId()
        exam = if (!TextUtils.isEmpty(stepId)) {
            stepId?.let { viewModel.getExamByStepId(it) }
        } else {
            id?.let { viewModel.getExamById(it) }
        }
    }

    var isLastAnsvalid = false
    fun checkAnsAndContinue(cont: Boolean) {
        if (cont) {
            isLastAnsvalid = true
            currentIndex += 1
            continueExam()
        } else {
            isLastAnsvalid = false
            val toast = Toast.makeText(activity, getString(R.string.incorrect_ans), Toast.LENGTH_SHORT)
            toast.show()
            viewLifecycleOwner.lifecycleScope.launch {
                delay(1000)
                toast.cancel()
            }
        }
    }

    private fun continueExam() {
        if (currentIndex < (questions?.size ?: 0)) {
            startExam(questions?.get(currentIndex))
        } else if (isTeam && type?.startsWith("survey") == true) {
            showUserInfoDialog()
        } else {
            saveCourseProgress(exam?.courseId, stepNumber, sub?.status == "graded", user?.id)
            val titleView = TextView(requireContext()).apply {
                text = "${getString(R.string.thank_you_for_taking_this)}$type! ${getString(R.string.we_wish_you_all_the_best)}"
                textSize = 18f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(20, 25, 20, 0)
            }

            AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                .setCustomTitle(titleView)
                .setPositiveButton(getString(R.string.finish)) { _: DialogInterface?, _: Int ->
                    FragmentNavigator.popBackStack(parentFragmentManager)
                }.setCancelable(false).show()
        }
    }

    abstract fun saveCourseProgress(courseId: String?, stepNum: Int, isGraded: Boolean, userId: String?)

    private fun showUserInfoDialog() {
        if (!isMySurvey && exam?.isFromNation != true) {
            UserInformationFragment.getInstance(sub?.id, teamId, exam?.isFromNation != true).show(childFragmentManager, "")
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.updateSubmissionStatus(sub?.id, "complete")
                sub?.status = "complete"
                Utilities.toast(activity, getString(R.string.thank_you_for_taking_this_survey))
                navigateToSurveyList(requireActivity())
            }
        }
    }

    companion object {
        fun navigateToSurveyList(activity: FragmentActivity) {
            val surveyListFragment = SurveyFragment()
            FragmentNavigator.replaceFragment(
                activity.supportFragmentManager,
                R.id.fragment_container,
                surveyListFragment
            )
        }
    }
    abstract fun startExam(question: ExamQuestion?)
    private fun insertIntoSubmitPhotos(submitId: String?) {
        viewModel.addSubmissionPhoto(
            submitId,
            exam?.id,
            exam?.courseId,
            user?.id,
            photoPath
        )
    }

    override fun onImageCapture(fileUri: String?) {
        photoPath = fileUri
        insertIntoSubmitPhotos(submitId)
    }

    fun setMarkdownViewAndShowInput(etAnswer: EditText, type: String, oldAnswer: String?) {
        if (currentAnswerEditText !== etAnswer) {
            releaseAnswerTextWatcher()
            currentAnswerEditText = etAnswer
        }
        etAnswer.visibility = View.VISIBLE
        // One watcher per EditText, attached until onDestroyView: it has to see every setText, plain-text
        // questions included, or a render still in flight from the last textarea lands on the new answer.
        val isMarkdown = type.equals("textarea", ignoreCase = true)
        val watcher = answerTextWatcher
        if (watcher != null) {
            watcher.isRenderingEnabled = isMarkdown
        } else if (isMarkdown) {
            answerTextWatcher = MarkdownPreRenderTextWatcher(markwonEditor, getMarkwonExecutor(), etAnswer)
                .also { etAnswer.addTextChangedListener(it) }
        }
        etAnswer.setText(oldAnswer)
    }

    private fun releaseAnswerTextWatcher() {
        answerTextWatcher?.let {
            it.cancel()
            currentAnswerEditText?.removeTextChangedListener(it)
        }
        answerTextWatcher = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releaseAnswerTextWatcher()
        currentAnswerEditText = null
    }

    override fun onDestroy() {
        markwonExecutor?.shutdown()
        markwonExecutor = null
        CameraUtils.release()
        super.onDestroy()
    }
}
