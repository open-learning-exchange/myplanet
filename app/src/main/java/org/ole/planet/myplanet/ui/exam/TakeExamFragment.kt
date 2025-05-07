package org.ole.planet.myplanet.ui.exam

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.Sort
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTakeExamBinding
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCertification.Companion.isCourseCertified
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.createSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.JsonParserUtils.getStringAsJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.KeyboardUtils.hideSoftKeyboard
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import java.util.Arrays
import java.util.Date
import java.util.Locale

class TakeExamFragment : BaseExamFragment(), View.OnClickListener, CompoundButton.OnCheckedChangeListener, ImageCaptureCallback {
    private lateinit var fragmentTakeExamBinding: FragmentTakeExamBinding
    private var isCertified = false
    var container: NestedScrollView? = null
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTakeExamBinding = FragmentTakeExamBinding.inflate(inflater, parent, false)
        listAns = HashMap()
        val dbHandler = UserProfileDbHandler(requireActivity())
        user = dbHandler.userModel
        return fragmentTakeExamBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initExam()
        questions = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", exam?.id).findAll()
        fragmentTakeExamBinding.tvQuestionCount.text = getString(R.string.Q1, questions?.size)
        var q: RealmQuery<*> = mRealm.where(RealmSubmission::class.java)
            .equalTo("userId", user?.id)
            .equalTo("parentId", if (!TextUtils.isEmpty(exam?.courseId)) {
                id + "@" + exam?.courseId
            } else {
                id
            }).sort("startTime", Sort.DESCENDING)
        if (type == "exam") {
            q = q.equalTo("status", "pending")
        }
        sub = q.findFirst() as RealmSubmission?
        val courseId = exam?.courseId
        isCertified = isCourseCertified(mRealm, courseId)
        if ((questions?.size ?: 0) > 0) {
            createSubmission()
            startExam(questions?.get(currentIndex))
        } else {
            container?.visibility = View.GONE
            fragmentTakeExamBinding.btnSubmit.visibility = View.GONE
            fragmentTakeExamBinding.tvQuestionCount.setText(R.string.no_questions)
            Snackbar.make(fragmentTakeExamBinding.tvQuestionCount, R.string.no_questions_available, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun createSubmission() {
        mRealm.executeTransaction { realm ->
            sub = createSubmission(null, realm)
            if (!TextUtils.isEmpty(exam?.id)) {
                sub?.parentId = if (!TextUtils.isEmpty(exam?.courseId)) {
                    "${exam?.id}@${exam?.courseId}"
                } else {
                    exam?.id
                }
            } else if (!TextUtils.isEmpty(id)) {
                sub?.parentId = if (!TextUtils.isEmpty(exam?.courseId)) {
                    "$id@${exam?.courseId}"
                } else {
                    id
                }
            }
            sub?.userId = user?.id
            sub?.status = "pending"
            sub?.type = type
            sub?.startTime = Date().time
            sub?.lastUpdateTime = Date().time
            if (sub?.answers == null) {
                sub?.answers = RealmList()
            }

            currentIndex = 0
            if (isTeam == true && teamId != null) {
                sub?.team = teamId
                val membershipDoc = realm.createObject(RealmMembershipDoc::class.java)
                membershipDoc.teamId = teamId
                sub?.membershipDoc = membershipDoc

                val userModel = UserProfileDbHandler(requireActivity()).userModel

                try {
                    val userJson = JSONObject()
                    userJson.put("age", userModel?.dob ?: "")
                    userJson.put("gender", userModel?.gender ?: "")
                    val membershipJson = JSONObject()
                    membershipJson.put("teamId", teamId)
                    userJson.put("membershipDoc", membershipJson)

                    sub?.user = userJson.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun startExam(question: RealmExamQuestion?) {
        fragmentTakeExamBinding.tvQuestionCount.text = getString(R.string.Q, currentIndex + 1, questions?.size)
        setButtonText()
        fragmentTakeExamBinding.groupChoices.removeAllViews()
        fragmentTakeExamBinding.llCheckbox.removeAllViews()
        fragmentTakeExamBinding.etAnswer.visibility = View.GONE
        fragmentTakeExamBinding.groupChoices.visibility = View.GONE
        fragmentTakeExamBinding.llCheckbox.visibility = View.GONE
        clearAnswer()
        if ((sub?.answers?.size ?: 0) > currentIndex) {
            ans = sub?.answers?.get(currentIndex)?.value ?: ""
        }
        if (question?.type.equals("select", ignoreCase = true)) {
            fragmentTakeExamBinding.groupChoices.visibility = View.VISIBLE
            fragmentTakeExamBinding.etAnswer.visibility = View.GONE
            selectQuestion(question, ans)
        } else if (question?.type.equals("input", ignoreCase = true) ||
            question?.type.equals("textarea", ignoreCase = true)) {
            question?.type?.let {
                setMarkdownViewAndShowInput(fragmentTakeExamBinding.etAnswer, it, ans)
            }
        } else if (question?.type.equals("selectMultiple", ignoreCase = true)) {
            fragmentTakeExamBinding.llCheckbox.visibility = View.VISIBLE
            fragmentTakeExamBinding.etAnswer.visibility = View.GONE
            showCheckBoxes(question, ans)
        }
        fragmentTakeExamBinding.tvHeader.text = question?.header
        question?.body?.let { setMarkdownText(fragmentTakeExamBinding.tvBody, it) }
        fragmentTakeExamBinding.btnSubmit.setOnClickListener(this)
    }

    private fun clearAnswer() {
        ans = ""
        fragmentTakeExamBinding.etAnswer.setText(R.string.empty_text)
        listAns?.clear()
    }

    private fun setButtonText() {
        if (currentIndex == (questions?.size?.minus(1) ?: 0)) {
            fragmentTakeExamBinding.btnSubmit.setText(R.string.finish)
        } else {
            fragmentTakeExamBinding.btnSubmit.setText(R.string.submit)
        }
    }

    private fun showCheckBoxes(question: RealmExamQuestion?, oldAnswer: String) {
        val choices = getStringAsJsonArray(question?.choices)
        for (i in 0 until choices.size()) {
            addCompoundButton(choices[i].asJsonObject, false, oldAnswer)
        }
    }

    private fun selectQuestion(question: RealmExamQuestion?, oldAnswer: String) {
        val choices = getStringAsJsonArray(question?.choices)
        for (i in 0 until choices.size()) {
            if (choices[i].isJsonObject) {
                addCompoundButton(choices[i].asJsonObject, true, oldAnswer)
            } else {
                addRadioButton(getString(choices, i), oldAnswer)
            }
        }
    }

    private fun addRadioButton(choice: String, oldAnswer: String) {
        val inflater = LayoutInflater.from(activity)
        val rdBtn = inflater.inflate(R.layout.item_radio_btn, fragmentTakeExamBinding.groupChoices, false) as RadioButton
        rdBtn.text = choice
        rdBtn.isChecked = choice == oldAnswer
        rdBtn.setOnCheckedChangeListener(this)
        fragmentTakeExamBinding.groupChoices.addView(rdBtn)
    }

    private fun addCompoundButton(choice: JsonObject?, isRadio: Boolean, oldAnswer: String) {
        val rdBtn = LayoutInflater.from(activity).inflate(
            if (isRadio) {
                R.layout.item_radio_btn
            } else {
                R.layout.item_checkbox
            }, null
        ) as CompoundButton
        rdBtn.text = getString("text", choice)
        rdBtn.tag = getString("id", choice)
        rdBtn.isChecked = getString("id", choice) == oldAnswer
        rdBtn.setOnCheckedChangeListener(this)
        if (isRadio) {
            fragmentTakeExamBinding.groupChoices.addView(rdBtn)
        } else {
            rdBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
            rdBtn.buttonTintList = ContextCompat.getColorStateList(requireContext(), R.color.daynight_textColor)
            fragmentTakeExamBinding.llCheckbox.addView(rdBtn)
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.btn_submit) {
            if (questions != null && currentIndex in 0 until (questions?.size ?: 0)) {
                val type = questions?.get(currentIndex)?.type
                showTextInput(type)
                if (showErrorMessage(getString(R.string.please_select_write_your_answer_to_continue))) {
                    return
                }
                val cont = updateAnsDb()
                capturePhoto()
                hideSoftKeyboard(requireActivity())
                checkAnsAndContinue(cont)
            }
        }
    }

    private fun capturePhoto() {
        try {
            if (isCertified && !isMySurvey) {
                capturePhoto(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showTextInput(type: String?) {
        if (type.equals("input", ignoreCase = true) || type.equals("textarea", ignoreCase = true)) {
            ans = fragmentTakeExamBinding.etAnswer.text.toString()
        }
    }

    private fun updateAnsDb(): Boolean {
        var flag = false
        mRealm.executeTransaction { realm ->
            sub?.status = if (currentIndex == (questions?.size ?: 0) - 1) {
                if (sub?.type == "survey") {
                    "complete"
                } else {
                    "requires grading"
                }
            } else {
                "pending"
            }
            if (isTeam == true) {
                sub?.team = teamId
            }
            val list: RealmList<RealmAnswer>? = sub?.answers
            val answer = createAnswer(list)
            val que = questions?.get(currentIndex)?.let { mRealm.copyFromRealm(it) }
            answer?.questionId = que?.id
            answer?.value = ans
            answer?.setValueChoices(listAns, isLastAnsvalid)
            answer?.submissionId = sub?.id
            submitId = answer?.submissionId ?: ""
            if ((que?.getCorrectChoice()?.size ?: 0) == 0) {
                answer?.grade = 0
                answer?.mistakes = 0
                flag = true
            } else {
                flag = checkCorrectAns(answer, que)
            }
            removeOldAnswer(list)
            list?.add(currentIndex, answer)
            sub?.answers = list
        }
        return flag
    }

    private fun removeOldAnswer(list: RealmList<RealmAnswer>?) {
        if (sub?.type == "survey" && (list?.size ?: 0) > currentIndex) {
            list?.removeAt(currentIndex)
        } else if ((list?.size ?: 0) > currentIndex && !isLastAnsvalid) {
            list?.removeAt(currentIndex)
        }
    }

    private fun checkCorrectAns(answer: RealmAnswer?, que: RealmExamQuestion?): Boolean {
        var flag = false
        answer?.isPassed = que?.getCorrectChoice()?.contains(ans.lowercase(Locale.getDefault())) == true
        answer?.grade = 1
        var mistake = answer?.mistakes
        val selectedAns = listAns?.values?.toTypedArray<String>()
        val correctChoices = que?.getCorrectChoice()?.toTypedArray<String>()
        if (!isEqual(selectedAns, correctChoices)) {
            if (mistake != null) {
                mistake += 1
            }
        } else {
            flag = true
        }
        if (answer != null) {
            answer.mistakes = mistake ?: 0
        }
        return flag
    }

    private fun isEqual(ar1: Array<String>?, ar2: Array<String>?): Boolean {
        ar1?.let { Arrays.sort(it) }
        ar2?.let { Arrays.sort(it) }
        return ar1.contentEquals(ar2)
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        if (b) {
            addAnswer(compoundButton)
        } else if (compoundButton.tag != null) {
            listAns?.remove("${compoundButton.text}")
        }
    }
}
