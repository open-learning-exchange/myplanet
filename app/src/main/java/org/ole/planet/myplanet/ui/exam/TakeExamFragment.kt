package org.ole.planet.myplanet.ui.exam

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
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
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmQuery
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.CameraUtils.capturePhoto
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
    private val gson = Gson()

    private val answerCache = mutableMapOf<String, AnswerData>()
    private var submissionReady = CompletableDeferred<Unit>()

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
            // Show questions immediately
            startExam(questions?.get(currentIndex))
            updateNavButtons()

            // Check if submission already exists or create new one
            Log.d("TakeExamFragment", "Checking existing submission: sub=$sub, id=${sub?.id}")
            if (sub == null) {
                Log.d("TakeExamFragment", "No existing submission, creating new one in background")
                // Create submission in background
                viewLifecycleOwner.lifecycleScope.launch {
                    val startTime = System.currentTimeMillis()
                    try {
                        Log.d("TakeExamFragment", "About to call createSubmissionAsync... [T+0ms]")
                        createSubmissionAsync()
                        val submitDuration = System.currentTimeMillis() - startTime
                        Log.d("TakeExamFragment", "createSubmissionAsync completed in ${submitDuration}ms, calling cleanupOldSubmissions...")
                        // Clean up old submissions after creation
                        val cleanupStart = System.currentTimeMillis()
                        cleanupOldSubmissions()
                        val cleanupDuration = System.currentTimeMillis() - cleanupStart
                        Log.d("TakeExamFragment", "cleanupOldSubmissions completed in ${cleanupDuration}ms (total: ${System.currentTimeMillis() - startTime}ms)")
                    } catch (e: Exception) {
                        val errorDuration = System.currentTimeMillis() - startTime
                        Log.e("TakeExamFragment", "Error in onViewCreated submission creation after ${errorDuration}ms", e)
                        e.printStackTrace()
                    }
                }
            } else {
                Log.d("TakeExamFragment", "Using existing submission id=${sub?.id}, marking as ready immediately")
                // Submission already exists, mark as ready immediately
                submissionReady.complete(Unit)
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
            question?.type.equals("input", ignoreCase = true) ||
                    question?.type.equals("textarea", ignoreCase = true) -> {
                question?.type?.let {
                    setMarkdownViewAndShowInput(binding.etAnswer, it, ans)
                }
            }
            question?.type.equals("selectMultiple", ignoreCase = true) -> {
                binding.llCheckbox.visibility = View.VISIBLE
                showCheckBoxes(question, ans)
                for (i in 0 until binding.llCheckbox.childCount) {
                    val child = binding.llCheckbox.getChildAt(i)
                    if (child is CompoundButton) {
                        val choiceText = child.text.toString()
                        child.isChecked = listAns?.containsKey(choiceText) == true
                    }
                }
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
                    listAns?.clear()
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
            clearAnswer()
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
                val otherChoice = gson.fromJson("""{"text":"Other","id":"other"}""", JsonObject::class.java)

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
        val clickTime = System.currentTimeMillis()
        Log.d("TakeExamFragment", "onClick triggered at T+${clickTime}ms, view.id=${view.id}, R.id.btn_submit=${R.id.btn_submit}")
        if (view.id == R.id.btn_submit) {
            Log.d("TakeExamFragment", "Submit button clicked, questions size=${questions?.size}, currentIndex=$currentIndex")
            if (questions != null && currentIndex in 0 until (questions?.size ?: 0)) {
                saveCurrentAnswer()
                Log.d("TakeExamFragment", "Current answer saved")

                if (!isQuestionAnswered()) {
                    Log.d("TakeExamFragment", "Question not answered, showing toast")
                    toast(activity, getString(R.string.please_select_write_your_answer_to_continue), Toast.LENGTH_SHORT)
                    return
                }

                Log.d("TakeExamFragment", "Launching coroutine for submission [T+${System.currentTimeMillis() - clickTime}ms], submissionReady.isCompleted=${submissionReady.isCompleted}")
                // On submit, ensure all cached answers are saved to DB
                viewLifecycleOwner.lifecycleScope.launch {
                    val launchTime = System.currentTimeMillis()
                    try {
                        Log.d("TakeExamFragment", "Waiting for submission to be ready (max 5 seconds)... [T+${launchTime - clickTime}ms]")
                        // Wait max 5 seconds for submission to be ready
                        val waitStart = System.currentTimeMillis()
                        withTimeout(5000) {
                            submissionReady.await()
                        }
                        val waitDuration = System.currentTimeMillis() - waitStart
                        Log.d("TakeExamFragment", "Submission ready after ${waitDuration}ms! sub=$sub, answerCache size=${answerCache.size}")

                        if (sub != null) {
                            Log.d("TakeExamFragment", "Saving all cached answers")
                            saveAllCachedAnswers()
                            Log.d("TakeExamFragment", "All cached answers saved")
                        } else {
                            Log.w("TakeExamFragment", "sub is null, skipping saveAllCachedAnswers")
                        }

                        val cont = updateAnsDb()
                        Log.d("TakeExamFragment", "updateAnsDb returned: $cont")

                        if (type == "exam" && !cont) {
                            Log.d("TakeExamFragment", "Exam answer incorrect, showing snackbar")
                            Snackbar.make(binding.root, getString(R.string.incorrect_ans), Snackbar.LENGTH_LONG).show()
                            return@launch
                        }

                        Log.d("TakeExamFragment", "Capturing photo and continuing...")
                        capturePhoto()
                        hideSoftKeyboard(requireActivity())
                        checkAnsAndContinue(cont)
                        Log.d("TakeExamFragment", "checkAnsAndContinue called")
                    } catch (e: Exception) {
                        val errorTime = System.currentTimeMillis()
                        Log.e("TakeExamFragment", "Error in submit coroutine (or timeout) at T+${errorTime - clickTime}ms", e)
                        e.printStackTrace()
                        // If submission failed or timed out, still allow continuing with cached answers
                        // The answers are in cache and will be saved on next attempt
                        Log.d("TakeExamFragment", "Continuing despite error/timeout, calling checkAnsAndContinue... [T+${errorTime - clickTime}ms]")
                        capturePhoto()
                        hideSoftKeyboard(requireActivity())
                        val continueStart = System.currentTimeMillis()
                        checkAnsAndContinue(true)
                        val continueDuration = System.currentTimeMillis() - continueStart
                        Log.d("TakeExamFragment", "checkAnsAndContinue returned after ${continueDuration}ms [Total: T+${System.currentTimeMillis() - clickTime}ms]")
                    }
                }
            } else {
                Log.w("TakeExamFragment", "Cannot submit: questions=${questions != null}, index in range=${currentIndex in 0 until (questions?.size ?: 0)}")
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


    private suspend fun saveAllCachedAnswers() {
        // Save all cached answers to the database SYNCHRONOUSLY on background thread
        Log.d("TakeExamFragment", "saveAllCachedAnswers: ${answerCache.size} answers in cache, sub status BEFORE: ${sub?.status}")

        if (sub == null) {
            Log.w("TakeExamFragment", "Cannot save answers - sub is null!")
            return
        }

        // Run transaction on background thread to avoid UI thread blocking
        withContext(Dispatchers.IO) {
            mRealm.executeTransaction { realm ->
            answerCache.forEach { (questionId, answerData) ->
                val question = questions?.find { it.id == questionId } ?: return@forEach

                val currentAns: String
                val currentListAns: HashMap<String, String>?
                val currentOtherText: String?

                when (question.type) {
                    "select", "ratingScale" -> {
                        currentAns = answerData.singleAnswer
                        currentListAns = null
                        currentOtherText = if (answerData.otherText.isNotEmpty()) answerData.otherText else null
                    }
                    "selectMultiple" -> {
                        currentAns = ""
                        currentListAns = answerData.multipleAnswers
                        currentOtherText = if (answerData.otherText.isNotEmpty()) answerData.otherText else null
                    }
                    else -> { // "input", "textarea"
                        currentAns = answerData.singleAnswer
                        currentListAns = null
                        currentOtherText = null
                    }
                }

                // Find the index of this question
                val questionIndex = questions?.indexOfFirst { it.id == questionId } ?: -1
                if (questionIndex >= 0) {
                    val isFinal = questionIndex == (questions?.size ?: 0) - 1
                    Log.d("TakeExamFragment", "Saving answer for question index=$questionIndex (total=${questions?.size}), isFinal=$isFinal")

                    // Save answer inline (synchronous, inside transaction)
                    val realmSubmission = realm.where(RealmSubmission::class.java).equalTo("id", sub?.id).findFirst()
                    val realmQuestion = realm.where(RealmExamQuestion::class.java).equalTo("id", question.id).findFirst()

                    if (realmSubmission != null && realmQuestion != null) {
                        // Create or retrieve answer
                        val existingAnswer = realmSubmission.answers?.find { it.questionId == question.id }
                        val answer = existingAnswer ?: realm.createObject(RealmAnswer::class.java, java.util.UUID.randomUUID().toString())
                        if (existingAnswer == null) {
                            realmSubmission.answers?.add(answer)
                        }
                        answer.questionId = question.id
                        answer.submissionId = realmSubmission.id

                        // Populate answer based on type
                        when (question.type) {
                            "select", "ratingScale" -> {
                                answer.value = currentAns
                                answer.valueChoices = RealmList<String>().apply {
                                    if (currentAns.isNotEmpty()) {
                                        add("""{"id":"$currentAns","text":"$currentAns"}""")
                                    }
                                }
                            }
                            "selectMultiple" -> {
                                answer.value = ""
                                answer.valueChoices = RealmList<String>().apply {
                                    currentListAns?.forEach { (text, id) ->
                                        add("""{"id":"$id","text":"$text"}""")
                                    }
                                }
                            }
                            else -> { // input, textarea
                                answer.value = currentAns
                                answer.valueChoices = null
                            }
                        }

                        // Update submission status
                        realmSubmission.lastUpdateTime = Date().time
                        if (isFinal && type == "survey") {
                            realmSubmission.status = "complete"
                            Log.d("TakeExamFragment", "Set submission status to COMPLETE (final question of survey)")
                        }
                    }
                }
            }
        }

        Log.d("TakeExamFragment", "saveAllCachedAnswers completed. Final submission status: ${sub?.status}")
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

        // Only save to DB if submission is ready, otherwise answer stays in cache
        if (!submissionReady.isCompleted) {
            // Submission not ready yet, but answer is already in answerCache
            // Return true for surveys, as we don't check correctness during navigation
            return true
        }

        return ExamSubmissionUtils.saveAnswer(
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

    private suspend fun createSubmissionAsync() {
        val startTime = System.currentTimeMillis()
        Log.d("TakeExamFragment", "createSubmissionAsync started [T+0ms]")

        withContext(Dispatchers.IO) {
            try {
                Log.d("TakeExamFragment", "Running transaction on background thread [T+${System.currentTimeMillis() - startTime}ms]")

                // Use synchronous transaction on background thread (more reliable than async)
                mRealm.executeTransaction { realm ->
                    val inTxnTime = System.currentTimeMillis()
                    Log.d("TakeExamFragment", "Inside transaction [T+${inTxnTime - startTime}ms]")

                    // Create new submission
                    val submission = createSubmission(null, realm)
                    submission.userId = user?.id
                    submission.status = "pending"
                    submission.type = type
                    submission.startTime = Date().time
                    submission.lastUpdateTime = Date().time

                    if (submission.answers == null) {
                        submission.answers = RealmList()
                    }

                    // Set parent ID
                    submission.parentId = when {
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
                        else -> submission.parentId
                    }

                    // Add team information if needed
                    if (isTeam && teamId != null) {
                        submission.team = teamId
                        val membershipDoc = realm.createObject(RealmMembershipDoc::class.java)
                        membershipDoc.teamId = teamId
                        submission.membershipDoc = membershipDoc

                        val userModel = userProfileDbHandler.userModel
                        try {
                            val userJson = JSONObject()
                            userJson.put("age", userModel?.dob ?: "")
                            userJson.put("gender", userModel?.gender ?: "")
                            val membershipJson = JSONObject()
                            membershipJson.put("teamId", teamId)
                            userJson.put("membershipDoc", membershipJson)
                            submission.user = userJson.toString()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val txnDuration = System.currentTimeMillis() - inTxnTime
                    Log.d("TakeExamFragment", "Transaction completed in ${txnDuration}ms")
                }

                // Switch back to main thread to update UI
                withContext(Dispatchers.Main) {
                    val successTime = System.currentTimeMillis()
                    Log.d("TakeExamFragment", "Submission creation SUCCESS [T+${successTime - startTime}ms]")

                    // Re-query the submission from the same Realm instance
                    val q: RealmQuery<*> = mRealm.where(RealmSubmission::class.java)
                        .equalTo("userId", user?.id)
                        .equalTo("parentId", if (!TextUtils.isEmpty(exam?.courseId)) {
                            id + "@" + exam?.courseId
                        } else {
                            id
                        }).sort("startTime", Sort.DESCENDING)
                    sub = q.findFirst() as RealmSubmission?
                    val queryTime = System.currentTimeMillis()
                    Log.d("TakeExamFragment", "Submission queried in ${queryTime - successTime}ms: sub=$sub, id=${sub?.id}")

                    // Mark submission as ready
                    submissionReady.complete(Unit)
                    val completeTime = System.currentTimeMillis()
                    Log.d("TakeExamFragment", "submissionReady completed [Total: ${completeTime - startTime}ms]")
                }
            } catch (e: Exception) {
                val errorTime = System.currentTimeMillis()
                Log.e("TakeExamFragment", "Submission creation FAILED after ${errorTime - startTime}ms", e)
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    answerCache.clear()
                    clearAnswer()
                    ans = ""
                    listAns?.clear()
                    sub = null
                    currentIndex = 0

                    // Mark submission as ready even on error, so finish button doesn't hang
                    submissionReady.complete(Unit)
                    Log.d("TakeExamFragment", "submissionReady completed (on error) [Total: ${System.currentTimeMillis() - startTime}ms]")
                }
                throw e
            }
        }
    }

    private fun cleanupOldSubmissions() {
        mRealm.executeTransactionAsync { realm ->
            // Delete old submissions in background (fire and forget)
            val parentIdToSearch = if (!TextUtils.isEmpty(exam?.courseId)) {
                "${exam?.id ?: id}@${exam?.courseId}"
            } else {
                exam?.id ?: id
            }

            val allSubmissions = realm.where(RealmSubmission::class.java)
                .equalTo("userId", user?.id)
                .equalTo("parentId", parentIdToSearch)
                .sort("startTime", Sort.DESCENDING)
                .findAll()

            // Keep the newest one (skip first), delete the rest
            if (allSubmissions.size > 1) {
                for (i in 1 until allSubmissions.size) {
                    allSubmissions[i]?.answers?.deleteAllFromRealm()
                    allSubmissions[i]?.deleteFromRealm()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveCurrentAnswer()

        // Save all cached answers if submission is ready
        if (submissionReady.isCompleted && sub != null) {
            saveAllCachedAnswers()
        }

        selectedRatingButton = null
        _binding = null
    }
}
