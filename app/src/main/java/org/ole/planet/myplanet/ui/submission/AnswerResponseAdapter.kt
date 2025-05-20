package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.realm.RealmList
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemQuestionResponseBinding
import org.ole.planet.myplanet.model.RealmAnswer

class AnswerResponseAdapter(
    private val answers: RealmList<RealmAnswer>?,
    private val questions: List<Any?>?
) : RecyclerView.Adapter<AnswerResponseAdapter.AnswerViewHolder>() {

    class AnswerViewHolder(val binding: ItemQuestionResponseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnswerViewHolder {
        val binding = ItemQuestionResponseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AnswerViewHolder(binding)
    }

    override fun getItemCount(): Int = answers?.size ?: 0

    override fun onBindViewHolder(holder: AnswerViewHolder, position: Int) {
        val answer = answers?.get(position)
        val question = findCorrespondingQuestion(answer?.questionId)

        with(holder.binding) {
            // Display question text
            tvQuestionText.text = question?.let {
                if (it is Map<*, *>) {
                    it["body"] as? String ?: "Question not available"
                } else {
                    "Question not available"
                }
            } ?: "Question not available"

            // Display answer based on type
//            when (answer.type) {
//                "select", "selectMultiple" -> {
//                    // Handle multiple choice answers
//                    val choices = question?.let {
//                        if (it is Map<*, *> && it["choices"] is List<*>) {
//                            it["choices"] as List<*>
//                        } else null
//                    }
//
//                    val selectedValue = answer.value as? String ?: ""
//                    val selectedChoice = choices?.find { choice ->
//                        if (choice is Map<*, *>) {
//                            choice["id"] as? String == selectedValue
//                        } else false
//                    }
//
//                    if (selectedChoice is Map<*, *>) {
//                        tvAnswerText.text = selectedChoice["text"] as? String ?: "No answer selected"
//                    } else {
//                        tvAnswerText.text = selectedValue.ifEmpty { "No answer selected" }
//                    }
//                }
//                "text" -> {
//                    // Handle text answers
//                    tvAnswerText.text = answer?.value as? String ?: "No answer provided"
//                }
//                else -> {
//                    tvAnswerText.text = "Answer type not supported"
//                }
//            }
        }
    }

    private fun findCorrespondingQuestion(questionId: String?): Any? {
        if (questionId == null || questions == null) return null

        return questions.find { question ->
            if (question is Map<*, *>) {
                val id = question["_id"] as? String ?: question["id"] as? String
                id == questionId
            } else false
        }
    }
}