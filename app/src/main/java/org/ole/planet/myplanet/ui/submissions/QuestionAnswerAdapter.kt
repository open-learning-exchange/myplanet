package org.ole.planet.myplanet.ui.submissions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.ItemQuestionAnswerBinding
import org.ole.planet.myplanet.utilities.DiffUtils

class QuestionAnswerAdapter : ListAdapter<QuestionAnswer, QuestionAnswerAdapter.ViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuestionAnswerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val qa = getItem(position)
        holder.bind(qa)
    }

    class ViewHolder(private val binding: ItemQuestionAnswerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(qa: QuestionAnswer) {
            if (!qa.questionHeader.isNullOrEmpty()) {
                binding.tvQuestionHeader.visibility = View.VISIBLE
                binding.tvQuestionHeader.text = qa.questionHeader
            } else {
                binding.tvQuestionHeader.visibility = View.GONE
            }

            binding.tvQuestionBody.text = qa.questionBody ?: "No question text"

            val answerText = formatAnswer(qa)
            binding.tvAnswerValue.text = answerText

            if (qa.questionType != null) {
                binding.tvQuestionType.visibility = View.VISIBLE
                binding.tvQuestionType.text = "Type: ${qa.questionType}"
            } else {
                binding.tvQuestionType.visibility = View.GONE
            }
        }

        private fun formatAnswer(qa: QuestionAnswer): String {
            return qa.answer ?: "No answer provided"
        }
    }

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<QuestionAnswer>(
            areItemsTheSame = { oldItem, newItem -> oldItem.questionId == newItem.questionId },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
