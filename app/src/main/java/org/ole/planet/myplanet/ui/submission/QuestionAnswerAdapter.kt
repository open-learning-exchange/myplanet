package org.ole.planet.myplanet.ui.submission

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.ItemQuestionAnswerBinding

class QuestionAnswerAdapter : RecyclerView.Adapter<QuestionAnswerAdapter.ViewHolder>() {
    private var questionAnswerInfos = mutableListOf<QuestionAnswerInfo>()

    fun updateData(infos: List<QuestionAnswerInfo>) {
        questionAnswerInfos.clear()
        questionAnswerInfos.addAll(infos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuestionAnswerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(questionAnswerInfos[position])
    }

    override fun getItemCount(): Int = questionAnswerInfos.size

    class ViewHolder(private val binding: ItemQuestionAnswerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(info: QuestionAnswerInfo) {
            if (!info.questionHeader.isNullOrEmpty()) {
                binding.tvQuestionHeader.visibility = View.VISIBLE
                binding.tvQuestionHeader.text = info.questionHeader
            } else {
                binding.tvQuestionHeader.visibility = View.GONE
            }

            binding.tvQuestionBody.text = info.questionBody ?: "No question text"
            binding.tvAnswerValue.text = info.answer?.value ?: "No answer provided"

            if (info.type != null) {
                binding.tvQuestionType.visibility = View.VISIBLE
                binding.tvQuestionType.text = "Type: ${info.type}"
            } else {
                binding.tvQuestionType.visibility = View.GONE
            }
        }
    }
}
