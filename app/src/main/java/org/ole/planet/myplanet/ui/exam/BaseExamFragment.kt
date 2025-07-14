package org.ole.planet.myplanet.ui.exam

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.realm.Realm
import io.realm.RealmResults
import java.util.Date
import java.util.UUID
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.NetworkUtils.getUniqueIdentifier
import org.ole.planet.myplanet.utilities.Utilities

abstract class BaseExamFragment : Fragment(), ImageCaptureCallback {
    var exam: RealmStepExam? = null
    lateinit var db: DatabaseService
    lateinit var mRealm: Realm
    var stepId: String? = null
    var id: String? = ""
    var type: String? = "exam"
    var currentIndex = 0
    private var stepNumber = 0
    var questions: RealmResults<RealmExamQuestion>? = null
    var ans = ""
    var user: RealmUserModel? = null
    var sub: RealmSubmission? = null
    var listAns: HashMap<String, String>? = null
    var isMySurvey = false
    private var uniqueId = getUniqueIdentifier()
    var date = Date().toString()
    private var photoPath: String? = ""
    var submitId = ""
    var isTeam: Boolean = false
    var teamId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseService(requireActivity())
        mRealm = db.realmInstance
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
                sub = mRealm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()
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
        exam = if (!TextUtils.isEmpty(stepId)) {
            mRealm.where(RealmStepExam::class.java).equalTo("stepId", stepId).findFirst()
        } else {
            mRealm.where(RealmStepExam::class.java).equalTo("id", id).findFirst()
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
            Handler(Looper.getMainLooper()).postDelayed({
                toast.cancel()
            }, 1000)
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
                    parentFragmentManager.popBackStack()
                }.setCancelable(false).show()
        }
    }

    private fun saveCourseProgress() {
        val progress = mRealm.where(RealmCourseProgress::class.java)
            .equalTo("courseId", exam?.courseId)
            .equalTo("stepNum", stepNumber).findFirst()
        if (progress != null) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            progress.passed = sub?.status == "graded"
            mRealm.commitTransaction()
        }
    }

    private fun showUserInfoDialog() {
        if (!isMySurvey && !exam?.isFromNation!!) {
            UserInformationFragment.getInstance(sub?.id, teamId, !isMySurvey && !exam?.isFromNation!!).show(childFragmentManager, "")
        } else {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            sub?.status = "complete"
            mRealm.commitTransaction()
            Utilities.toast(activity, getString(R.string.thank_you_for_taking_this_survey))
            navigateToSurveyList(requireActivity())
        }
    }

    companion object {
        fun navigateToSurveyList(activity: FragmentActivity) {
            val surveyListFragment = SurveyFragment()
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, surveyListFragment)
                .commit()
        }
    }

    fun addAnswer(compoundButton: CompoundButton) {
        val btnText = compoundButton.text.toString()
        val btnId = compoundButton.tag?.toString() ?: ""

        if (compoundButton is RadioButton) {
            ans = btnId
        } else if (compoundButton is CheckBox) {
            listAns?.put(btnText, btnId)
        }
    }

    abstract fun startExam(question: RealmExamQuestion?)
    private fun insertIntoSubmitPhotos(submitId: String?) {
        mRealm.beginTransaction()
        val submit = mRealm.createObject(RealmSubmitPhotos::class.java, UUID.randomUUID().toString())
        submit.submissionId = submitId
        submit.examId = exam?.id
        submit.courseId = exam?.courseId
        submit.memberId = user?.id
        submit.date = date
        submit.uniqueId = uniqueId
        submit.photoLocation = photoPath
        submit.uploaded = false
        mRealm.commitTransaction()
    }

    override fun onImageCapture(fileUri: String?) {
        photoPath = fileUri
        insertIntoSubmitPhotos(submitId)
    }

    fun setMarkdownViewAndShowInput(etAnswer: EditText, type: String, oldAnswer: String?) {
        etAnswer.visibility = View.VISIBLE
        val markwon = Markwon.create(requireActivity())
        val editor = MarkwonEditor.create(markwon)
        if (type.equals("textarea", ignoreCase = true)) {
            etAnswer.addTextChangedListener(MarkwonEditorTextWatcher.withProcess(editor))
        } else {
            etAnswer.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun afterTextChanged(editable: Editable) {}
            })
        }
        etAnswer.setText(oldAnswer)
    }
}
