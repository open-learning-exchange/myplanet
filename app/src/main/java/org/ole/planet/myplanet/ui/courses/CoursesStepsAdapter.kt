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

class CoursesStepsAdapter(
    private val context: Context,
    private val onStepClicked: (String) -> Unit
) : ListAdapter<StepItem, CoursesStepsAdapter.ViewHolder>(STEP_ITEM_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoursesStepsAdapter.ViewHolder {
        val rowStepsBinding = RowStepsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(rowStepsBinding)
    }

    override fun onBindViewHolder(holder: CoursesStepsAdapter.ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: CoursesStepsAdapter.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            if (payloads.first() is Boolean) {
                val isDescriptionVisible = payloads.first() as Boolean
                holder.updateDescriptionVisibility(isDescriptionVisible)
            }
        }
    }

    inner class ViewHolder(private val rowStepsBinding: RowStepsBinding) : RecyclerView.ViewHolder(rowStepsBinding.root) {
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.id?.let { stepId ->
                        onStepClicked(stepId)
                    }
                }
            }
        }

        fun bind(step: StepItem) {
            rowStepsBinding.tvTitle.text = step.stepTitle
            rowStepsBinding.tvDescription.text = context.getString(R.string.test_size, step.questionCount)
            updateDescriptionVisibility(step.isDescriptionVisible)
        }

        fun updateDescriptionVisibility(isVisible: Boolean) {
            if (isVisible) {
                rowStepsBinding.tvDescription.visibility = View.VISIBLE
            } else {
                rowStepsBinding.tvDescription.visibility = View.GONE
            }
        }
    }

    companion object {
        private val STEP_ITEM_COMPARATOR = DiffUtils.itemCallback<StepItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem },
            getChangePayload = { oldItem, newItem ->
                if (oldItem.isDescriptionVisible != newItem.isDescriptionVisible) {
                    newItem.isDescriptionVisible
                } else {
                    null
                }
            }
        )
    }
}
