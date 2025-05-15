package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.ole.planet.myplanet.R

//class QuestionAdapter(private val questions: List<Question>, private val answers: Map<String, Answer>) :
//    RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder>() {
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.question_display_item, parent, false)
//        return QuestionViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
//        val question = questions[position]
//        val answer = answers[question._id]
//        holder.bind(question, answer)
//    }
//
//    override fun getItemCount() = questions.size
//
//    inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        private val tvQuestionHeader: TextView = itemView.findViewById(R.id.tvQuestionHeader)
//        private val tvQuestionBody: TextView = itemView.findViewById(R.id.tvQuestionBody)
//        private val chipQuestionType: Chip = itemView.findViewById(R.id.chipQuestionType)
//        private val tvTextAnswer: TextView = itemView.findViewById(R.id.tvTextAnswer)
//        private val chipSingleChoice: Chip = itemView.findViewById(R.id.chipSingleChoice)
//        private val chipGroupMultipleChoice: ChipGroup = itemView.findViewById(R.id.chipGroupMultipleChoice)
//        private val tvTextareaAnswer: TextView = itemView.findViewById(R.id.tvTextareaAnswer)
//        private val tvNoAnswer: TextView = itemView.findViewById(R.id.tvNoAnswer)
//
//        fun bind(question: Question, answer: Answer?) {
//            // Set question details
//            tvQuestionHeader.text = question.header
//            tvQuestionHeader.visibility = if (question.header.isNotEmpty()) View.VISIBLE else View.GONE
//            tvQuestionBody.text = question.body
//            chipQuestionType.text = question.type
//
//            // Set chip color based on question type
//            val colorRes = when (question.type) {
//                "input" -> R.color.blue_400
//                "select" -> R.color.green_400
//                "selectMultiple" -> R.color.purple_400
//                "textarea" -> R.color.amber_400
//                else -> R.color.gray_400
//            }
//            chipQuestionType.setChipBackgroundColorResource(colorRes)
//
//            // Reset all answer views
//            tvTextAnswer.visibility = View.GONE
//            chipSingleChoice.visibility = View.GONE
//            chipGroupMultipleChoice.visibility = View.GONE
//            tvTextareaAnswer.visibility = View.GONE
//            tvNoAnswer.visibility = View.GONE
//
//            // If no answer, show the no answer view
//            if (answer == null) {
//                tvNoAnswer.visibility = View.VISIBLE
//                return
//            }
//
//            // Display the appropriate answer view based on question type
//            when (question.type) {
//                "input" -> {
//                    tvTextAnswer.text = answer.value as String
//                    tvTextAnswer.visibility = View.VISIBLE
//                }
//                "select" -> {
//                    val selectedId = answer.value as String
//                    val selectedChoice = question.choices.find { it.id == selectedId }
//                    chipSingleChoice.text = selectedChoice?.text ?: selectedId
//                    chipSingleChoice.visibility = View.VISIBLE
//                }
//                "selectMultiple" -> {
//                    val selectedIds = answer.value as List<String>
//                    chipGroupMultipleChoice.removeAllViews()
//
//                    selectedIds.forEach { choiceId ->
//                        val choice = question.choices.find { it.id == choiceId }
//                        val chip = Chip(itemView.context)
//                        chip.text = choice?.text ?: choiceId
//                        chipGroupMultipleChoice.addView(chip)
//                    }
//
//                    chipGroupMultipleChoice.visibility = if (selectedIds.isNotEmpty()) View.VISIBLE else View.GONE
//                    if (selectedIds.isEmpty()) tvNoAnswer.visibility = View.VISIBLE
//                }
//                "textarea" -> {
//                    tvTextareaAnswer.text = answer.value as String
//                    tvTextareaAnswer.visibility = View.VISIBLE
//                }
//            }
//        }
//    }
//}

class QuestionAdapter(
    private val questions: List<Any>,  // Replace with your actual question type
    private val answers: List<Any>     // Replace with your actual answer type
) : RecyclerView.Adapter<QuestionAdapter.QuestionResponseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionResponseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.question_display_item, parent, false)
        return QuestionResponseViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuestionResponseViewHolder, position: Int) {
        val question = questions.getOrNull(position)
        // Find the matching answer for this question
        val answer = answers.find { it.questionId == question?.id }
        holder.bind(question, answer)
    }

    override fun getItemCount(): Int = questions.size

    class QuestionResponseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvQuestionNumber: TextView = view.findViewById(R.id.tvQuestionNumber)
        private val tvQuestionText: TextView = view.findViewById(R.id.tvQuestionText)
        private val tvQuestionType: TextView = view.findViewById(R.id.tvQuestionType)
        private val responseContainer: LinearLayout = view.findViewById(R.id.responseContainer)

        fun bind(question: Any?, answer: Any?) {
            // Cast to your actual types as needed
            if (question == null) {
                tvQuestionNumber.text = "N/A"
                tvQuestionText.text = "Question not available"
                tvQuestionType.text = "Unknown"
                responseContainer.removeAllViews()
                return
            }

            // Set the question number (position + 1)
            tvQuestionNumber.text = "Q${adapterPosition + 1}"

            // Set question text - adjust based on your actual Question model
            tvQuestionText.text = (question as? Map<*, *>)?.get("body") as? String ?: "Unknown question"

            // Set question type
            val questionType = (question as? Map<*, *>)?.get("type") as? String ?: "unknown"
            tvQuestionType.text = formatQuestionType(questionType)

            // Clear previous response views
            responseContainer.removeAllViews()

            // Create and add response view based on question type and answer
            val responseView = createResponseView(question, answer, responseContainer.context)
            if (responseView != null) {
                responseContainer.addView(responseView)
            }
        }

        private fun formatQuestionType(type: String): String {
            return when (type.lowercase()) {
                "input" -> "Short Answer"
                "textarea" -> "Long Answer"
                "select" -> "Single Choice"
                "selectmultiple" -> "Multiple Choice"
                else -> type.capitalize()
            }
        }

        private fun createResponseView(question: Any, answer: Any?, context: Context): View? {
            val questionMap = question as? Map<*, *> ?: return null
            val type = questionMap["type"] as? String ?: return null

            return when (type.lowercase()) {
                "input" -> {
                    // Short text response
                    TextView(context).apply {
                        text = (answer as? Map<*, *>)?.get("value") as? String ?: "No response"
                        textSize = 16f
                        setPadding(0, 8, 0, 8)
                    }
                }
                "textarea" -> {
                    // Long text response
                    TextView(context).apply {
                        text = (answer as? Map<*, *>)?.get("value") as? String ?: "No response"
                        textSize = 16f
                        setPadding(0, 8, 0, 8)
                    }
                }
                "select" -> {
                    // Single choice response
                    val selectedValue = (answer as? Map<*, *>)?.get("value") as? String
                    val choices = questionMap["choices"] as? List<*> ?: emptyList<Any>()
                    val selectedChoice = choices.find {
                        (it as? Map<*, *>)?.get("id") == selectedValue
                    } as? Map<*, *>

                    TextView(context).apply {
                        text = selectedChoice?.get("text") as? String ?: "No selection"
                        textSize = 16f
                        setPadding(0, 8, 0, 8)
                    }
                }
                "selectmultiple" -> {
                    // Multiple choice response
                    val selectedValues = (answer as? Map<*, *>)?.get("value") as? List<*> ?: emptyList<Any>()
                    val choices = questionMap["choices"] as? List<*> ?: emptyList<Any>()

                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL

                        // Create a checkbox for each choice
                        choices.forEach { choice ->
                            val choiceMap = choice as? Map<*, *> ?: return@forEach
                            val choiceId = choiceMap["id"] as? String ?: return@forEach
                            val choiceText = choiceMap["text"] as? String ?: "Option"
                            val isSelected = selectedValues.contains(choiceId)

                            val checkBox = CheckBox(context).apply {
                                text = choiceText
                                isChecked = isSelected
                                isEnabled = false  // Make it non-interactive
                                setPadding(0, 4, 0, 4)
                            }

                            addView(checkBox)
                        }
                    }
                }
                else -> {
                    TextView(context).apply {
                        text = "Unsupported question type"
                        textSize = 16f
                    }
                }
            }
        }
    }
}