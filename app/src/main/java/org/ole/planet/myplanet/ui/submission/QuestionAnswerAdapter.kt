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
import org.ole.planet.myplanet.model.dto.QuestionAnswerViewModel
import org.ole.planet.myplanet.model.dto.QuestionChoice

class QuestionAnswerAdapter : ListAdapter<QuestionAnswerViewModel, QuestionAnswerAdapter.ViewHolder>(QuestionAnswerDiffCallback()) {
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
        fun bind(pair: QuestionAnswerViewModel) {
            val question = pair.question
            val answer = pair.answer

            if (!question.header.isNullOrEmpty()) {
                binding.tvQuestionHeader.visibility = View.VISIBLE
                binding.tvQuestionHeader.text = question.header
            } else {
                binding.tvQuestionHeader.visibility = View.GONE
            }

            binding.tvQuestionBody.text = question.body ?: "No question text"

            val answerText = formatAnswer(answer, pair.answerChoiceTexts)
            binding.tvAnswerValue.text = answerText

            if (question.type != null) {
                binding.tvQuestionType.visibility = View.VISIBLE
                binding.tvQuestionType.text = "Type: ${question.type}"
            } else {
                binding.tvQuestionType.visibility = View.GONE
            }
        }

        private fun formatAnswer(answer: RealmAnswer?, answerChoiceTexts: List<String>): String {
            if (answer == null) {
                return "No answer provided"
            }

            return when {
                !answer.value.isNullOrEmpty() -> {
                    answer.value!!
                }
                answerChoiceTexts.isNotEmpty() -> {
                    answerChoiceTexts.joinToString(", ")
                }
                else -> "No answer provided"
            }
        }
    }
}

class QuestionAnswerDiffCallback : DiffUtil.ItemCallback<QuestionAnswerViewModel>() {
    override fun areItemsTheSame(oldItem: QuestionAnswerViewModel, newItem: QuestionAnswerViewModel): Boolean {
        return oldItem.question.id == newItem.question.id
    }

    override fun areContentsTheSame(oldItem: QuestionAnswerViewModel, newItem: QuestionAnswerViewModel): Boolean {
        return oldItem == newItem
    }
}
