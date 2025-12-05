package org.ole.planet.myplanet.ui.submission

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.databinding.ItemQuestionAnswerBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion

data class QuestionAnswerPair(
    val question: RealmExamQuestion,
    val answer: RealmAnswer?
)

class QuestionAnswerAdapter : ListAdapter<QuestionAnswerPair, QuestionAnswerAdapter.ViewHolder>(QuestionAnswerDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuestionAnswerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pair = getItem(position)
        holder.bind(pair)
        if (position < 5 || position >= itemCount - 2) {
            Log.d("RecyclerViewDebug", "Binding item at position $position")
        }
    }

    class ViewHolder(private val binding: ItemQuestionAnswerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(pair: QuestionAnswerPair) {
            val question = pair.question
            val answer = pair.answer

            if (!question.header.isNullOrEmpty()) {
                binding.tvQuestionHeader.visibility = View.VISIBLE
                binding.tvQuestionHeader.text = question.header
            } else {
                binding.tvQuestionHeader.visibility = View.GONE
            }

            binding.tvQuestionBody.text = question.body ?: "No question text"

            val answerText = formatAnswer(answer, question)
            binding.tvAnswerValue.text = answerText

            if (question.type != null) {
                binding.tvQuestionType.visibility = View.VISIBLE
                binding.tvQuestionType.text = "Type: ${question.type}"
            } else {
                binding.tvQuestionType.visibility = View.GONE
            }
        }

        private fun formatAnswer(answer: RealmAnswer?, question: RealmExamQuestion): String {
            if (answer == null) {
                return "No answer provided"
            }

            return when {
                !answer.value.isNullOrEmpty() -> {
                    answer.value!!
                }
                answer.valueChoices != null && answer.valueChoices!!.isNotEmpty() -> {
                    formatMultipleChoiceAnswer(answer.valueChoices!!, question)
                }
                else -> "No answer provided"
            }
        }

        private fun formatMultipleChoiceAnswer(choices: List<String>, question: RealmExamQuestion): String {
            val selectedChoices = mutableListOf<String>()

            try {
                val questionChoicesJson = if (!question.choices.isNullOrEmpty()) {
                    Gson().fromJson(question.choices, JsonArray::class.java)
                } else {
                    JsonArray()
                }

                for (choice in choices) {
                    try {
                        val choiceJson = Gson().fromJson(choice, JsonObject::class.java)
                        val choiceId = choiceJson.get("id")?.asString
                        val choiceText = choiceJson.get("text")?.asString

                        if (!choiceText.isNullOrEmpty()) {
                            selectedChoices.add(choiceText)
                        } else if (!choiceId.isNullOrEmpty()) {
                            val matchingChoice = findChoiceTextById(choiceId, questionChoicesJson)
                            if (matchingChoice != null) {
                                selectedChoices.add(matchingChoice)
                            } else {
                                selectedChoices.add(choiceId)
                            }
                        }
                    } catch (e: Exception) {
                        selectedChoices.add(choice)
                    }
                }
            } catch (e: Exception) {
                return choices.joinToString(", ")
            }

            return if (selectedChoices.isNotEmpty()) {
                selectedChoices.joinToString(", ")
            } else {
                "No selection made"
            }
        }

        private fun findChoiceTextById(choiceId: String, questionChoices: JsonArray): String? {
            for (i in 0 until questionChoices.size()) {
                try {
                    val choice = questionChoices[i].asJsonObject
                    if (choice.get("id")?.asString == choiceId) {
                        return choice.get("text")?.asString
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            return null
        }
    }
}

class QuestionAnswerDiffCallback : DiffUtil.ItemCallback<QuestionAnswerPair>() {
    override fun areItemsTheSame(oldItem: QuestionAnswerPair, newItem: QuestionAnswerPair): Boolean {
        return oldItem.question.id == newItem.question.id
    }

    override fun areContentsTheSame(oldItem: QuestionAnswerPair, newItem: QuestionAnswerPair): Boolean {
        return oldItem == newItem
    }
}
