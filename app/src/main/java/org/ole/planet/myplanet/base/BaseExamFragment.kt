package org.ole.planet.myplanet.base

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.surveys.SurveyFragment
import org.ole.planet.myplanet.utils.CameraUtils
import org.ole.planet.myplanet.utils.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.utils.NetworkUtils.getUniqueIdentifier
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
abstract class BaseExamFragment : Fragment(), ImageCaptureCallback {
    var exam: RealmStepExam? = null
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var submissionsRepository: SubmissionsRepository
    @Inject
    lateinit var coursesRepository: CoursesRepository
    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope
    var stepId: String? = null
    var id: String? = ""
    var type: String? = "exam"
    var currentIndex = 0
    private var stepNumber = 0
    var questions: List<RealmExamQuestion>? = null
    var ans = ""
    var user: RealmUser? = null
    var sub: RealmSubmission? = null
    var listAns: HashMap<String, String>? = null
    var isMySurvey = false
    private var uniqueId = getUniqueIdentifier()
    var date = Date().toString()
    private var photoPath: String? = ""
    var submitId = ""
    var isTeam: Boolean = false
    var teamId: String? = null
    internal var answerTextWatcher: TextWatcher? = null
    private var currentAnswerEditText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            stepId = requireArguments().getString("stepId")
            stepNumber = requireArguments().getInt("stepNum")
            isMySurvey = requireArguments().getBoolean("isMySurvey")
            isTeam = requireArguments().getBoolean("isTeam", false)
            teamId = requireArguments().getString("teamId")
            checkId()
            checkType()
        }
    }

    private fun checkId() {
        if (TextUtils.isEmpty(stepId)) {
            id = requireArguments().getString("id")
            if (isMySurvey) {
                sub = databaseService.withRealm { realm ->
                    realm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()
                        ?.let { realm.copyFromRealm(it) }
                }
                id = if (sub?.parentId?.contains("@") == true) {
                    sub?.parentId?.split("@".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()?.get(0)
                } else {
                    sub?.parentId
                }
            }
        }
    }

    private fun checkType() {
        if (requireArguments().containsKey("type")) {
            type = requireArguments().getString("type")
        }
    }

    fun initExam() {
        exam = databaseService.withRealm { realm ->
            if (!TextUtils.isEmpty(stepId)) {
                realm.where(RealmStepExam::class.java).equalTo("stepId", stepId).findFirst()
                    ?.let { realm.copyFromRealm(it) }
            } else {
                realm.where(RealmStepExam::class.java).equalTo("id", id).findFirst()
                    ?.let { realm.copyFromRealm(it) }
            }
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
        } else if (isTeam == true && type?.startsWith("survey") == true) {
            showUserInfoDialog()
        } else {
            saveCourseProgress()
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

    private fun saveCourseProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            coursesRepository.updateCourseProgress(exam?.courseId, stepNumber, sub?.status == "graded")
        }
    }

    private fun showUserInfoDialog() {
        if (!isMySurvey && exam?.isFromNation != true) {
            UserInformationFragment.getInstance(sub?.id, teamId, exam?.isFromNation != true).show(childFragmentManager, "")
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                submissionsRepository.updateSubmissionStatus(sub?.id, "complete")
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
    abstract fun startExam(question: RealmExamQuestion?)
    private fun insertIntoSubmitPhotos(submitId: String?) {
        applicationScope.launch {
            submissionsRepository.addSubmissionPhoto(
                submitId,
                exam?.id,
                exam?.courseId,
                user?.id,
                photoPath
            )
        }
    }

    override fun onImageCapture(fileUri: String?) {
        photoPath = fileUri
        insertIntoSubmitPhotos(submitId)
    }

    fun setMarkdownViewAndShowInput(etAnswer: EditText, type: String, oldAnswer: String?) {
        currentAnswerEditText?.removeTextChangedListener(answerTextWatcher)
        currentAnswerEditText = etAnswer
        etAnswer.visibility = View.VISIBLE
        val markwon = Markwon.create(requireActivity())
        val editor = MarkwonEditor.create(markwon)
        if (type.equals("textarea", ignoreCase = true)) {
            answerTextWatcher = MarkwonEditorTextWatcher.withProcess(editor)
            etAnswer.addTextChangedListener(answerTextWatcher)
        } else {
            answerTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun afterTextChanged(editable: Editable) {}
            }
            etAnswer.addTextChangedListener(answerTextWatcher)
        }
        etAnswer.setText(oldAnswer)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentAnswerEditText?.removeTextChangedListener(answerTextWatcher)
    }

    override fun onDestroy() {
        CameraUtils.release()
        super.onDestroy()
    }
}
