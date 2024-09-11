package org.ole.planet.myplanet.ui.exam

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
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
import java.util.Date
import java.util.UUID

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseService(requireActivity())
        mRealm = db.realmInstance
        if (arguments != null) {
            stepId = requireArguments().getString("stepId")
            stepNumber = requireArguments().getInt("stepNum")
            isMySurvey = requireArguments().getBoolean("isMySurvey")
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
            Utilities.toast(activity, getString(R.string.incorrect_ans))
        }
    }

    private fun continueExam() {
        if (currentIndex < (questions?.size ?: 0)) {
            startExam(questions?.get(currentIndex))
        } else if (type?.startsWith("survey") == true) {
            showUserInfoDialog()
        } else {
            saveCourseProgress()
            AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                .setTitle(getString(R.string.thank_you_for_taking_this) + type + getString(R.string.we_wish_you_all_the_best))
                .setPositiveButton("Finish") { _: DialogInterface?, _: Int ->
                    parentFragmentManager.popBackStack()
                    try {
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.show()
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
            UserInformationFragment.getInstance(sub?.id).show(childFragmentManager, "")
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

    fun showErrorMessage(s: String?): Boolean {
        if (ans.isEmpty() && listAns?.isEmpty() != false) {
            if (s != null) {
                Utilities.toast(activity, s)
            }
            return true
        }
        return false
    }

    fun createAnswer(answerList: RealmList<RealmAnswer>?): RealmAnswer? {
        var list = answerList
        if (list == null) {
            list = RealmList()
        }
        val answer: RealmAnswer? = if (list.size > currentIndex) {
            list[currentIndex]
        } else {
            mRealm.createObject(
                RealmAnswer::class.java,
                UUID.randomUUID().toString()
            )
        }
        return answer
    }

    fun addAnswer(compoundButton: CompoundButton) {
        if (compoundButton.tag != null) {
            listAns?.set(compoundButton.text.toString() + "", compoundButton.tag.toString() + "")
        } else {
            ans = compoundButton.text.toString() + ""
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
