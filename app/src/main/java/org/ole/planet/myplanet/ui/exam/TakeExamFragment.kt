package org.ole.planet.myplanet.ui.exam

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.Sort
import io.realm.Realm
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
import org.ole.planet.myplanet.ui.exam.ExamAnswerUtils
import org.ole.planet.myplanet.ui.exam.ExamSubmissionUtils
import org.ole.planet.myplanet.utilities.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.JsonParserUtils.getStringAsJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.KeyboardUtils.hideSoftKeyboard
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.Utilities.toast
import java.util.Date
import java.util.Locale
import java.util.UUID

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
            updateNavButtons()
        } else {
            container?.visibility = View.GONE
            fragmentTakeExamBinding.btnSubmit.visibility = View.GONE
            fragmentTakeExamBinding.tvQuestionCount.setText(R.string.no_questions)
            Snackbar.make(fragmentTakeExamBinding.tvQuestionCount, R.string.no_questions_available, Snackbar.LENGTH_LONG).show()
        }

        fragmentTakeExamBinding.btnBack.setOnClickListener {
            saveCurrentAnswer()
            goToPreviousQuestion()
        }
        fragmentTakeExamBinding.btnNext.setOnClickListener {
            saveCurrentAnswer()
            goToNextQuestion()
        }

        fragmentTakeExamBinding.etAnswer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNavButtons()
            }
        })
    }

    private fun saveCurrentAnswer() {
        val type = questions?.get(currentIndex)?.type
        showTextInput(type)
        updateAnsDb()
    }

    private fun goToPreviousQuestion() {
        saveCurrentAnswer()

        if (currentIndex > 0) {
            currentIndex--
            startExam(questions?.get(currentIndex))
            updateNavButtons()
        }
    }

    private fun goToNextQuestion() {
        saveCurrentAnswer()

        if (currentIndex < (questions?.size ?: 0) - 1) {
            currentIndex++
            startExam(questions?.get(currentIndex))
            updateNavButtons()
        }
    }

    private fun updateNavButtons() {
        fragmentTakeExamBinding.btnBack.visibility = if (currentIndex == 0) View.GONE else View.VISIBLE
        val isLastQuestion = currentIndex == (questions?.size ?: 0) - 1
        val isCurrentQuestionAnswered = isQuestionAnswered()

        fragmentTakeExamBinding.btnNext.visibility = if (isLastQuestion || !isCurrentQuestionAnswered) View.GONE else View.VISIBLE

        setButtonText()
    }

    private fun isQuestionAnswered(): Boolean {
        val currentQuestion = questions?.get(currentIndex)
        val singleOtherOptionSelected = ans == "other"
        val multipleOtherOptionSelected = listAns?.containsKey("Other")
        val otherOptionSelected = singleOtherOptionSelected || multipleOtherOptionSelected == true
        val otherNotAnswered = fragmentTakeExamBinding.etAnswer.text.toString().isEmpty()
        if(currentQuestion?.hasOtherOption == true && otherOptionSelected && otherNotAnswered){
            return false
        }

        return when {
            currentQuestion?.type.equals("select", ignoreCase = true) -> {
                ans.isNotEmpty()
            }
            currentQuestion?.type.equals("selectMultiple", ignoreCase = true) -> {
                listAns?.isNotEmpty() == true
            }
            currentQuestion?.type.equals("input", ignoreCase = true) ||
                    currentQuestion?.type.equals("textarea", ignoreCase = true) -> {
                fragmentTakeExamBinding.etAnswer.text.toString().isNotEmpty()
            }
            else -> false
        }
    }

    private fun createSubmission() {
        mRealm.executeTransaction { realm ->
            sub = createSubmission(null, realm)
            setParentId()
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
                addTeamInformation(realm)
            }
        }
    }

    private fun setParentId() {
        sub?.parentId = when {
            !TextUtils.isEmpty(exam?.id) -> if (!TextUtils.isEmpty(exam?.courseId)) {
                "${exam?.id}@${exam?.courseId}"
            } else {
                exam?.id
            }
            !TextUtils.isEmpty(id) -> if (!TextUtils.isEmpty(exam?.courseId)) {
                "$id@${exam?.courseId}"
            } else {
                id
            }
            else -> sub?.parentId
        }
    }

    private fun addTeamInformation(realm: Realm) {
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

    override fun startExam(question: RealmExamQuestion?) {
        fragmentTakeExamBinding.tvQuestionCount.text = getString(R.string.Q, currentIndex + 1, questions?.size)
        setButtonText()
        fragmentTakeExamBinding.groupChoices.removeAllViews()
        fragmentTakeExamBinding.llCheckbox.removeAllViews()
        fragmentTakeExamBinding.etAnswer.visibility = View.GONE
        fragmentTakeExamBinding.groupChoices.visibility = View.GONE
        fragmentTakeExamBinding.llCheckbox.visibility = View.GONE
        clearAnswer()

        loadSavedAnswer()

        when {
            question?.type.equals("select", ignoreCase = true) -> {
                fragmentTakeExamBinding.groupChoices.visibility = View.VISIBLE
                fragmentTakeExamBinding.etAnswer.visibility = View.GONE
                selectQuestion(question, ans)
            }
            question?.type.equals("input", ignoreCase = true) ||
                    question?.type.equals("textarea", ignoreCase = true) -> {
                question?.type?.let {
                    setMarkdownViewAndShowInput(fragmentTakeExamBinding.etAnswer, it, ans)
                }
            }
            question?.type.equals("selectMultiple", ignoreCase = true) -> {
                fragmentTakeExamBinding.llCheckbox.visibility = View.VISIBLE
                fragmentTakeExamBinding.etAnswer.visibility = View.GONE
                showCheckBoxes(question, ans)
            }
        }
        fragmentTakeExamBinding.tvHeader.text = question?.header
        question?.body?.let { setMarkdownText(fragmentTakeExamBinding.tvBody, it) }
        fragmentTakeExamBinding.btnSubmit.setOnClickListener(this)

        updateNavButtons()
    }

    private fun loadSavedAnswer() {
        ans = ""
        listAns?.clear()

        val currentQuestion = questions?.get(currentIndex)
        val savedAnswer = sub?.answers?.find { it.questionId == currentQuestion?.id }

        if (savedAnswer != null) {
            when {
                currentQuestion?.type.equals("select", ignoreCase = true) -> loadSelectSavedAnswer(savedAnswer)
                currentQuestion?.type.equals("selectMultiple", ignoreCase = true) -> loadSelectMultipleSavedAnswer(savedAnswer)
                currentQuestion?.type.equals("input", ignoreCase = true) ||
                        currentQuestion?.type.equals("textarea", ignoreCase = true) -> loadTextSavedAnswer(savedAnswer)
            }
        }
    }

    private fun loadSelectSavedAnswer(savedAnswer: RealmAnswer) {
        ans = savedAnswer.valueChoices?.firstOrNull()?.let {
            try {
                val jsonObject = Gson().fromJson(it, JsonObject::class.java)
                val id = jsonObject.get("id").asString
                val text = jsonObject.get("text").asString

                if (id == "other") {
                    fragmentTakeExamBinding.etAnswer.setText(text)
                    fragmentTakeExamBinding.etAnswer.visibility = View.VISIBLE
                }
                id
            } catch (e: Exception) {
                e.printStackTrace()
                savedAnswer.value ?: ""
            }
        } ?: savedAnswer.value ?: ""
    }

    private fun loadSelectMultipleSavedAnswer(savedAnswer: RealmAnswer) {
        var hasOtherOption = false
        var otherText = ""

        savedAnswer.valueChoices?.forEach { choiceJson ->
            try {
                val jsonObject = Gson().fromJson(choiceJson, JsonObject::class.java)
                val id = jsonObject.get("id").asString
                val text = jsonObject.get("text").asString

                if (id == "other") {
                    hasOtherOption = true
                    otherText = text
                    listAns?.put("Other", id)
                } else {
                    listAns?.put(text, id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (hasOtherOption) {
            fragmentTakeExamBinding.etAnswer.setText(otherText)
            fragmentTakeExamBinding.etAnswer.visibility = View.VISIBLE
        }
    }

    private fun loadTextSavedAnswer(savedAnswer: RealmAnswer) {
        ans = savedAnswer.value ?: ""
        fragmentTakeExamBinding.etAnswer.setText(ans)
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

        if (question?.hasOtherOption == true) {
            val gson = Gson()
            val otherChoice = gson.fromJson("""{"text":"Other","id":"other"}""", JsonObject::class.java)

            addCompoundButton(otherChoice, false, oldAnswer)
        }
    }

    private fun selectQuestion(question: RealmExamQuestion?, oldAnswer: String) {
        val choices = getStringAsJsonArray(question?.choices)
        val isRadio = question?.type != "multiple"

        for (i in 0 until choices.size()) {
            if (choices[i].isJsonObject) {
                addCompoundButton(choices[i].asJsonObject, isRadio, oldAnswer)
            } else {
                addRadioButton(getString(choices, i), oldAnswer)
            }
        }

        if (question?.hasOtherOption == true) {
            if (choices.size() > 0 && choices[0].isJsonObject) {
                val gson = Gson()
                val otherChoice = gson.fromJson("""{"text":"Other","id":"other"}""", JsonObject::class.java)

                addCompoundButton(otherChoice, isRadio, oldAnswer)
            } else {
                addRadioButton("Other", oldAnswer)
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

        if (choice.equals("Other", ignoreCase = true) && choice == oldAnswer) {
            fragmentTakeExamBinding.etAnswer.visibility = View.VISIBLE
            fragmentTakeExamBinding.etAnswer.setText(oldAnswer)
        }
    }

    private fun addCompoundButton(choice: JsonObject?, isRadio: Boolean, oldAnswer: String) {
        val rdBtn = if (isRadio) {
            LayoutInflater.from(activity)
                .inflate(
                    R.layout.item_radio_btn,
                    fragmentTakeExamBinding.groupChoices, false
                ) as RadioButton
        } else {
            LayoutInflater.from(activity)
                .inflate(
                    R.layout.item_checkbox, null
                ) as CompoundButton
        }
        val choiceText = getString("text", choice)
        val choiceId = getString("id", choice)

        rdBtn.text = choiceText
        rdBtn.tag = choiceId

        if (isRadio) {
            rdBtn.isChecked = choiceId == oldAnswer
        } else {
            rdBtn.isChecked = listAns?.get(choiceText) == choiceId
        }

        rdBtn.setOnCheckedChangeListener(this)
        if (isRadio) {
            rdBtn.id = View.generateViewId()
            fragmentTakeExamBinding.groupChoices.addView(rdBtn)
        } else {
            rdBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
            rdBtn.buttonTintList = ContextCompat.getColorStateList(requireContext(), R.color.daynight_textColor)
            fragmentTakeExamBinding.llCheckbox.addView(rdBtn)
        }

        if (choiceText.equals("Other", ignoreCase = true) && rdBtn.isChecked) {
            fragmentTakeExamBinding.etAnswer.visibility = View.VISIBLE
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.btn_submit) {
            if (questions != null && currentIndex in 0 until (questions?.size ?: 0)) {
                val type = questions?.get(currentIndex)?.type
                showTextInput(type)
                if (!isQuestionAnswered()) {
                    toast(activity,getString(R.string.please_select_write_your_answer_to_continue), Toast.LENGTH_SHORT)
                    return
                }

                val cont = updateAnsDb()

                if (this.type == "exam" && !cont) {
                    Snackbar.make(fragmentTakeExamBinding.root, getString(R.string.incorrect_ans), Snackbar.LENGTH_LONG).show()
                    return
                }

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
        if (type.equals("input", ignoreCase = true) || type.equals("textarea", ignoreCase = true) ||
            (fragmentTakeExamBinding.etAnswer.isVisible)) {
            ans = fragmentTakeExamBinding.etAnswer.text.toString()
        }
    }

    private fun updateAnsDb(): Boolean {
        val currentQuestion = questions?.get(currentIndex) ?: return true
        val otherText = if (fragmentTakeExamBinding.etAnswer.isVisible) {
            fragmentTakeExamBinding.etAnswer.text.toString()
        } else {
            null
        }
        return ExamSubmissionUtils.saveAnswer(
            mRealm,
            sub,
            currentQuestion,
            ans,
            listAns,
            otherText,
            fragmentTakeExamBinding.etAnswer.isVisible,
            type ?: "exam",
            currentIndex,
            questions?.size ?: 0
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, isChecked: Boolean) {
        if (isChecked) {
            handleChecked(compoundButton)
        } else {
            handleUnchecked(compoundButton)
        }
        updateNavButtons()
    }

    private fun handleChecked(compoundButton: CompoundButton) {
        val selectedText = "${compoundButton.text}"

        if (selectedText.equals("Other", ignoreCase = true)) {
            fragmentTakeExamBinding.etAnswer.visibility = View.VISIBLE
            fragmentTakeExamBinding.etAnswer.requestFocus()
        } else if (!isOtherOptionSelected()) {
            fragmentTakeExamBinding.etAnswer.visibility = View.GONE
            fragmentTakeExamBinding.etAnswer.text.clear()
        }

        addAnswer(compoundButton)
    }

    private fun handleUnchecked(compoundButton: CompoundButton) {
        if (compoundButton.tag != null && compoundButton !is RadioButton) {
            val selectedText = "${compoundButton.text}"

            if (selectedText.equals("Other", ignoreCase = true)) {
                fragmentTakeExamBinding.etAnswer.visibility = View.GONE
                fragmentTakeExamBinding.etAnswer.text.clear()
            }

            listAns?.remove("${compoundButton.text}")
        }
    }

    private fun isOtherOptionSelected(): Boolean {
        for (i in 0 until fragmentTakeExamBinding.llCheckbox.childCount) {
            val child = fragmentTakeExamBinding.llCheckbox.getChildAt(i)
            if (child is CompoundButton &&
                child.text.toString().equals("Other", ignoreCase = true) &&
                child.isChecked) {
                return true
            }
        }
        return false
    }
}
