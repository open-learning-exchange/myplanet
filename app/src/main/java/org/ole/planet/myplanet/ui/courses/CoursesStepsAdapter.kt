package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowStepsBinding
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.utils.DiffUtils

class CoursesStepsAdapter(private val context: Context) : ListAdapter<StepItem, CoursesStepsAdapter.ViewHolder>(STEP_ITEM_COMPARATOR) {
    private val descriptionVisibilityMap = mutableMapOf<String, Boolean>()
    private var currentlyVisibleStepId: String? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val rowStepsBinding = RowStepsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(rowStepsBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            if (payloads.first() is Boolean) {
                holder.updateDescriptionVisibility()
            }
        }
    }

    override fun submitList(list: List<StepItem>?) {
        list?.forEach { step ->
            step.id?.let { descriptionVisibilityMap.getOrPut(it) { false } }
        }
        super.submitList(list)
    }

    inner class ViewHolder(private val rowStepsBinding: RowStepsBinding) : RecyclerView.ViewHolder(rowStepsBinding.root) {
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.id?.let { stepId ->
                        toggleDescriptionVisibility(stepId)
                    }
                }
            }
        }

        fun bind(step: StepItem) {
            rowStepsBinding.tvTitle.text = step.stepTitle
            rowStepsBinding.tvDescription.text = context.getString(R.string.test_size, step.questionCount)
            updateDescriptionVisibility()
        }

        fun updateDescriptionVisibility() {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val stepId = getItem(position)?.id
                if (stepId != null) {
                    if (descriptionVisibilityMap[stepId] == true) {
                        rowStepsBinding.tvDescription.visibility = View.VISIBLE
                    } else {
                        rowStepsBinding.tvDescription.visibility = View.GONE
                    }
                }
            }
        }

    }

    private fun toggleDescriptionVisibility(stepId: String) {
        val currentVisibility = descriptionVisibilityMap.getOrElse(stepId) { false }
        val newVisibility = !currentVisibility

        if (newVisibility) {
            currentlyVisibleStepId?.let {
                if (it != stepId) {
                    descriptionVisibilityMap[it] = false
                    val oldPosition = currentList.indexOfFirst { step -> step.id == it }
                    if (oldPosition != -1) notifyItemChanged(oldPosition, false)
                }
            }
            currentlyVisibleStepId = stepId
        } else if (currentlyVisibleStepId == stepId) {
            currentlyVisibleStepId = null
        }

        descriptionVisibilityMap[stepId] = newVisibility
        val position = currentList.indexOfFirst { it.id == stepId }
        if (position != -1) {
            notifyItemChanged(position, newVisibility)
        }
    }

    companion object {
        private val STEP_ITEM_COMPARATOR = DiffUtils.itemCallback<StepItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
