package org.ole.planet.myplanet.ui.exam

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTakeExamBinding
import org.ole.planet.myplanet.model.RealmCertification.Companion.isCourseCertified
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.createSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.JsonUtils.getStringAsJsonArray
import org.ole.planet.myplanet.utilities.KeyboardUtils.hideSoftKeyboard
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import org.ole.planet.myplanet.utilities.Utilities.toast

@AndroidEntryPoint
class TakeExamFragment : BaseExamFragment(), View.OnClickListener, CompoundButton.OnCheckedChangeListener, ImageCaptureCallback {
    private var _binding: FragmentTakeExamBinding? = null
    private val binding get() = _binding!!
    private var isCertified = false

    private val answerCache = mutableMapOf<String, AnswerData>()

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler

    data class AnswerData(
        var singleAnswer: String = "",
        var multipleAnswers: HashMap<String, String> = HashMap(),
        var otherText: String = ""
    )

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTakeExamBinding.inflate(inflater, parent, false)
        listAns = HashMap()
        user = userProfileDbHandler.userModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initExam()
        questions = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", exam?.id).findAll()
        binding.tvQuestionCount.text = getString(R.string.Q1, questions?.size)
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
            // Only clear previous submissions for exams, not surveys
            // Surveys should allow multiple submissions
            if (type == "exam") {
                clearAllExistingAnswers {
                    createSubmission()
                    startExam(questions?.get(currentIndex))
                    updateNavButtons()
                }
            } else {
                createSubmission()
                startExam(questions?.get(currentIndex))
                updateNavButtons()
            }
        } else {
            binding.container.visibility = View.GONE
            binding.btnSubmit.visibility = View.GONE
            binding.tvQuestionCount.setText(R.string.no_questions)
            Snackbar.make(binding.tvQuestionCount, R.string.no_questions_available, Snackbar.LENGTH_LONG).show()
        }

        binding.btnBack.setOnClickListener {
            saveCurrentAnswer()
            goToPreviousQuestion()
        }
        binding.btnNext.setOnClickListener {
            saveCurrentAnswer()
            goToNextQuestion()
        }


        binding.etAnswer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val questionsSize = questions?.size ?: 0
                if (currentIndex < 0 || currentIndex >= questionsSize) return

                val currentQuestion = questions?.get(currentIndex)
                currentQuestion?.id?.let { questionId ->
                    val answerData = answerCache.getOrPut(questionId) { AnswerData() }
                    when (currentQuestion.type) {
                        "input", "textarea" -> {
                            answerData.singleAnswer = s.toString()
                        }
                        else -> {
                            answerData.otherText = s.toString()
                        }
                    }
                }
                updateNavButtons()
            }
        })
    }

    private fun saveCurrentAnswer() {
        val questionsSize = questions?.size ?: 0
        if (currentIndex < 0 || currentIndex >= questionsSize) return

        val currentQuestion = questions?.get(currentIndex) ?: return
        val questionId = currentQuestion.id ?: return
        val answerData = answerCache.getOrPut(questionId) { AnswerData() }

        when (currentQuestion.type) {
            "select", "ratingScale" -> {
                answerData.singleAnswer = ans
                if (binding.etAnswer.isVisible) {
                    answerData.otherText = binding.etAnswer.text.toString()
                }
            }
            "selectMultiple" -> {
                answerData.multipleAnswers.clear()
                listAns?.let { answerData.multipleAnswers.putAll(it) }
                if (binding.etAnswer.isVisible) {
                    answerData.otherText = binding.etAnswer.text.toString()
                }
            }
            "input", "textarea" -> {
                answerData.singleAnswer = binding.etAnswer.text.toString()
            }
        }
        updateAnsDb()
    }

    private fun goToPreviousQuestion() {
        if (currentIndex > 0) {
            currentIndex--
            startExam(questions?.get(currentIndex))
            updateNavButtons()
        }
    }

    private fun goToNextQuestion() {
        if (currentIndex < (questions?.size ?: 0) - 1) {
            currentIndex++
            startExam(questions?.get(currentIndex))
            updateNavButtons()
        }
    }

    private fun updateNavButtons() {
        binding.btnBack.visibility = if (currentIndex == 0) View.GONE else View.VISIBLE
        val isLastQuestion = currentIndex == (questions?.size ?: 0) - 1
        val isCurrentQuestionAnswered = isQuestionAnswered()

        binding.btnNext.visibility = if (isLastQuestion || !isCurrentQuestionAnswered) View.GONE else View.VISIBLE

        setButtonText()
    }

    private fun isQuestionAnswered(): Boolean {
        val questionsSize = questions?.size ?: 0
        if (currentIndex < 0 || currentIndex >= questionsSize) return false

        val currentQuestion = questions?.get(currentIndex)
        val questionId = currentQuestion?.id ?: return false
        val answerData = answerCache[questionId]

        val singleOtherOptionSelected = ans == "other" || answerData?.singleAnswer == "other"
        val multipleOtherOptionSelected = listAns?.containsKey("Other") == true ||
                answerData?.multipleAnswers?.containsKey("Other") == true
        val otherOptionSelected = singleOtherOptionSelected || multipleOtherOptionSelected
        val otherText = answerData?.otherText ?: binding.etAnswer.text.toString()

        if (currentQuestion.hasOtherOption && otherOptionSelected && otherText.isEmpty()) {
            return false
        }

        return when (currentQuestion.type) {
            "select" -> {
                ans.isNotEmpty() || answerData?.singleAnswer?.isNotEmpty() == true
            }
            "selectMultiple" -> {
                listAns?.isNotEmpty() == true || answerData?.multipleAnswers?.isNotEmpty() == true
            }
            "input", "textarea" -> {
                binding.etAnswer.text.toString().isNotEmpty() || answerData?.singleAnswer?.isNotEmpty() == true
            }
            "ratingScale" -> {
                ans.isNotEmpty() || answerData?.singleAnswer?.isNotEmpty() == true
            }
            else -> false
        }
    }

    private fun createSubmission() {
        mRealm.beginTransaction()
        try {
            sub = createSubmission(null, mRealm)
            setParentId()
            setParentJson()
            sub?.userId = user?.id
            sub?.status = "pending"
            sub?.type = type
            sub?.startTime = Date().time
            sub?.lastUpdateTime = Date().time
            if (sub?.answers == null) {
                sub?.answers = RealmList()
            }

            currentIndex = 0
            if (isTeam && teamId != null) {
                addTeamInformation(mRealm)
            }
            mRealm.commitTransaction()
        } catch (e: Exception) {
            mRealm.cancelTransaction()
            throw e
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

    private fun setParentJson() {
        try {
            val parentJsonString = JSONObject().apply {
                put("_id", exam?.id ?: id)
                put("name", exam?.name ?: "")
                put("courseId", exam?.courseId ?: "")
                put("sourcePlanet", exam?.sourcePlanet ?: "")
                put("teamShareAllowed", exam?.isTeamShareAllowed ?: false)
                put("noOfQuestions", exam?.noOfQuestions ?: 0)
                put("isFromNation", exam?.isFromNation ?: false)
            }.toString()
            sub?.parent = parentJsonString
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addTeamInformation(realm: Realm) {
        val team = realm.where(org.ole.planet.myplanet.model.RealmMyTeam::class.java)
            .equalTo("_id", teamId)
            .findFirst()

        if (team != null) {
            val teamRef = realm.createObject(org.ole.planet.myplanet.model.RealmTeamReference::class.java)
            teamRef._id = team._id
            teamRef.name = team.name
            teamRef.type = team.type ?: "team"
            sub?.teamObject = teamRef
        }

        val membershipDoc = realm.createObject(RealmMembershipDoc::class.java)
        membershipDoc.teamId = teamId
        sub?.membershipDoc = membershipDoc

        val userModel = userProfileDbHandler.userModel

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
        binding.tvQuestionCount.text = getString(R.string.Q, currentIndex + 1, questions?.size)
        setButtonText()
        binding.groupChoices.removeAllViews()
        binding.llCheckbox.removeAllViews()
        binding.etAnswer.visibility = View.GONE
        binding.groupChoices.visibility = View.GONE
        binding.llCheckbox.visibility = View.GONE
        binding.llRatingScale.visibility = View.GONE

        loadSavedAnswer(question)

        when {
            question?.type.equals("select", ignoreCase = true) -> {
                binding.groupChoices.visibility = View.VISIBLE
                selectQuestion(question, ans)
            }
            question?.type.equals("input", ignoreCase = true) || question?.type.equals("textarea", ignoreCase = true) -> {
                question?.type?.let {
                    setMarkdownViewAndShowInput(binding.etAnswer, it, ans)
                    val questionId = question.id
                    val answerData = answerCache[questionId]
                    if (answerData != null && answerData.singleAnswer.isNotEmpty()) {
                        binding.etAnswer.setText(answerData.singleAnswer)
                    }
                }
            }
            question?.type.equals("selectMultiple", ignoreCase = true) -> {
                binding.llCheckbox.visibility = View.VISIBLE
                showCheckBoxes(question, ans)
            }
            question?.type.equals("ratingScale", ignoreCase = true) -> {
                binding.llRatingScale.visibility = View.VISIBLE
                setupRatingScale(ans)
            }
        }
        binding.tvHeader.text = question?.header
        question?.body?.let { setMarkdownText(binding.tvBody, it) }
        binding.btnSubmit.setOnClickListener(this)

        updateNavButtons()
    }

    private fun loadSavedAnswer(question: RealmExamQuestion?) {
        val questionId = question?.id ?: return
        val answerData = answerCache[questionId]

        ans = ""
        listAns?.clear()
        selectedRatingButton?.isSelected = false
        selectedRatingButton = null

        if (answerData != null) {
            when (question.type) {
                "select", "ratingScale" -> {
                    ans = answerData.singleAnswer
                    if (answerData.otherText.isNotEmpty()) {
                        binding.etAnswer.setText(answerData.otherText)
                        if (ans == "other") {
                            binding.etAnswer.visibility = View.VISIBLE
                        }
                    }
                }
                "selectMultiple" -> {
                    listAns?.putAll(answerData.multipleAnswers)
                    if (answerData.otherText.isNotEmpty()) {
                        binding.etAnswer.setText(answerData.otherText)
                        if (listAns?.containsKey("Other") == true) {
                            binding.etAnswer.visibility = View.VISIBLE
                        }
                    }
                }
                "input", "textarea" -> {
                    ans = answerData.singleAnswer
                    binding.etAnswer.setText(ans)
                }
            }
        } else {
            binding.etAnswer.setText("")
        }
    }

    private var selectedRatingButton: Button? = null
    
    private fun setupRatingScale(oldAnswer: String) {
        val ratingButtons = listOf(
            binding.rbRating1,
            binding.rbRating2,
            binding.rbRating3,
            binding.rbRating4,
            binding.rbRating5,
            binding.rbRating6,
            binding.rbRating7,
            binding.rbRating8,
            binding.rbRating9
        )
        
        ratingButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedRatingButton?.isSelected = false

                button.isSelected = true
                selectedRatingButton = button
                ans = (index + 1).toString()
                
                updateNavButtons()
            }
        }

        if (oldAnswer.isNotEmpty()) {
            selectRatingValue(oldAnswer.toIntOrNull() ?: 1)
        }
    }
    
    private fun selectRatingValue(value: Int) {
        val ratingButtons = listOf(
            binding.rbRating1,
            binding.rbRating2,
            binding.rbRating3,
            binding.rbRating4,
            binding.rbRating5,
            binding.rbRating6,
            binding.rbRating7,
            binding.rbRating8,
            binding.rbRating9
        )

        selectedRatingButton?.isSelected = false
        
        if (value in 1..9) {
            val button = ratingButtons[value - 1]
            button.isSelected = true
            selectedRatingButton = button
        }
    }

    private fun clearAnswer() {
        ans = ""
        binding.etAnswer.setText("")
        listAns?.clear()
        selectedRatingButton?.isSelected = false
        selectedRatingButton = null
    }

    private fun setButtonText() {
        if (currentIndex == (questions?.size?.minus(1) ?: 0)) {
            binding.btnSubmit.setText(R.string.finish)
        } else {
            binding.btnSubmit.setText(R.string.submit)
        }
    }

    private fun showCheckBoxes(question: RealmExamQuestion?, oldAnswer: String) {
        val choices = getStringAsJsonArray(question?.choices)

        for (i in 0 until choices.size()) {
            addCompoundButton(choices[i].asJsonObject, false, oldAnswer)
        }

        if (question?.hasOtherOption == true) {
            val otherChoice = GsonUtils.gson.fromJson("""{"text":"Other","id":"other"}""", JsonObject::class.java)

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
                val otherChoice = GsonUtils.gson.fromJson("""{"text":"Other","id":"other"}""", JsonObject::class.java)

                addCompoundButton(otherChoice, isRadio, oldAnswer)
            } else {
                addRadioButton("Other", oldAnswer)
            }
        }
    }

    private fun addRadioButton(choice: String, oldAnswer: String) {
        val inflater = LayoutInflater.from(activity)
        val rdBtn = inflater.inflate(R.layout.item_radio_btn, binding.groupChoices, false) as RadioButton
        rdBtn.text = choice
        rdBtn.isChecked = choice == oldAnswer
        rdBtn.setOnCheckedChangeListener(this)
        binding.groupChoices.addView(rdBtn)

        if (choice.equals("Other", ignoreCase = true) && choice == oldAnswer) {
            binding.etAnswer.visibility = View.VISIBLE
            binding.etAnswer.setText(oldAnswer)
        }
    }

    private fun addCompoundButton(choice: JsonObject?, isRadio: Boolean, oldAnswer: String) {
        val rdBtn = if (isRadio) {
            LayoutInflater.from(activity)
                .inflate(
                    R.layout.item_radio_btn,
                    binding.groupChoices, false
                ) as RadioButton
        } else {
            LayoutInflater.from(activity)
                .inflate(R.layout.item_checkbox, null) as CompoundButton
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
            binding.groupChoices.addView(rdBtn)
        } else {
            rdBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
            rdBtn.buttonTintList = ContextCompat.getColorStateList(requireContext(), R.color.daynight_textColor)
            binding.llCheckbox.addView(rdBtn)
        }

        if (choiceText.equals("Other", ignoreCase = true) && rdBtn.isChecked) {
            binding.etAnswer.visibility = View.VISIBLE
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.btn_submit) {
            if (questions != null && currentIndex in 0 until (questions?.size ?: 0)) {
                saveCurrentAnswer()

                if (!isQuestionAnswered()) {
                    toast(activity, getString(R.string.please_select_write_your_answer_to_continue), Toast.LENGTH_SHORT)
                    return
                }

                val cont = updateAnsDb()

                if (this.type == "exam" && !cont) {
                    Snackbar.make(binding.root, getString(R.string.incorrect_ans), Snackbar.LENGTH_LONG).show()
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


    private fun updateAnsDb(): Boolean {
        val questionsSize = questions?.size ?: 0
        if (currentIndex < 0 || currentIndex >= questionsSize) return true

        val currentQuestion = questions?.get(currentIndex) ?: return true
        val otherText = if (binding.etAnswer.isVisible) {
            binding.etAnswer.text.toString()
        } else {
            null
        }
        
        if (sub == null) {
            sub = mRealm.where(RealmSubmission::class.java)
                .equalTo("status", "pending")
                .findAll().lastOrNull()
        }

        val result = ExamSubmissionUtils.saveAnswer(
            mRealm,
            sub,
            currentQuestion,
            ans,
            listAns,
            otherText,
            binding.etAnswer.isVisible,
            type ?: "exam",
            currentIndex,
            questions?.size ?: 0
        )
        return result
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, isChecked: Boolean) {
        if (isChecked) {
            handleChecked(compoundButton)
        } else {
            handleUnchecked(compoundButton)
        }

        val questionsSize = questions?.size ?: 0
        if (currentIndex < 0 || currentIndex >= questionsSize) return

        val currentQuestion = questions?.get(currentIndex)
        currentQuestion?.id?.let { questionId ->
            val answerData = answerCache.getOrPut(questionId) { AnswerData() }

            if (currentQuestion.type == "selectMultiple") {
                answerData.multipleAnswers.clear()
                listAns?.let { answerData.multipleAnswers.putAll(it) }
            } else if (currentQuestion.type == "select") {
                answerData.singleAnswer = ans
            }
        }

        updateNavButtons()
    }

    private fun handleChecked(compoundButton: CompoundButton) {
        val selectedText = "${compoundButton.text}"

        if (selectedText.equals("Other", ignoreCase = true)) {
            binding.etAnswer.visibility = View.VISIBLE
            binding.etAnswer.requestFocus()
        } else if (!isOtherOptionSelected()) {
            binding.etAnswer.visibility = View.GONE
            binding.etAnswer.text.clear()
        }

        val choiceId = compoundButton.tag as? String
        if (compoundButton is RadioButton) {
            ans = choiceId ?: selectedText
        } else {
            listAns?.put(selectedText, choiceId ?: selectedText)
        }
    }

    private fun handleUnchecked(compoundButton: CompoundButton) {
        if (compoundButton.tag != null && compoundButton !is RadioButton) {
            val selectedText = "${compoundButton.text}"

            if (selectedText.equals("Other", ignoreCase = true)) {
                binding.etAnswer.visibility = View.GONE
                binding.etAnswer.text.clear()
            }

            listAns?.remove("${compoundButton.text}")
        }
    }

    private fun isOtherOptionSelected(): Boolean {
        for (i in 0 until binding.llCheckbox.childCount) {
            val child = binding.llCheckbox.getChildAt(i)
            if (child is CompoundButton &&
                child.text.toString().equals("Other", ignoreCase = true) &&
                child.isChecked) {
                return true
            }
        }
        return false
    }

    private fun clearAllExistingAnswers(onComplete: () -> Unit = {}) {
        val examIdValue = exam?.id
        val examCourseIdValue = exam?.courseId
        val userIdValue = user?.id

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                databaseService.executeTransactionAsync { realm ->
                    val parentIdToSearch = if (!TextUtils.isEmpty(examCourseIdValue)) {
                        "${examIdValue ?: id}@${examCourseIdValue}"
                    } else {
                        examIdValue ?: id
                    }

                    val allSubmissions = realm.where(RealmSubmission::class.java)
                        .equalTo("userId", userIdValue)
                        .equalTo("parentId", parentIdToSearch)
                        .findAll()

                    allSubmissions.forEach { submission ->
                        submission.answers?.deleteAllFromRealm()
                        submission.deleteFromRealm()
                    }
                }

                withContext(Dispatchers.Main) {
                    answerCache.clear()
                    clearAnswer()
                    ans = ""
                    listAns?.clear()
                    sub = null
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    answerCache.clear()
                    clearAnswer()
                    ans = ""
                    listAns?.clear()
                    sub = null
                    onComplete()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveCurrentAnswer()
        answerTextWatcher?.let { binding.etAnswer.removeTextChangedListener(it) }
        selectedRatingButton = null
        _binding = null
    }
}
