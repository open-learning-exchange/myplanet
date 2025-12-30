package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowStepsBinding
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.utilities.DiffUtils

class StepsAdapter(private val context: Context, private val submissionRepository: SubmissionsRepository, private val lifecycleOwner: LifecycleOwner) : ListAdapter<StepItem, StepsAdapter.ViewHolder>(STEP_ITEM_COMPARATOR) {
    private val descriptionVisibilityMap = mutableMapOf<String, Boolean>()
    private var currentlyVisibleStepId: String? = null
    private val examQuestionCountCache = mutableMapOf<String, Int>()
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
        private var loadJob: Job? = null

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
            rowStepsBinding.tvDescription.text = context.getString(R.string.test_size, 0)
            loadJob?.cancel()

            val stepId = step.id
            if (!stepId.isNullOrEmpty()) {
                val cachedCount = examQuestionCountCache[stepId]
                if (cachedCount != null) {
                    rowStepsBinding.tvDescription.text = context.getString(R.string.test_size, cachedCount)
                } else {
                    loadJob = lifecycleOwner.lifecycleScope.launch {
                        val size = withContext(Dispatchers.IO) {
                            submissionRepository.getExamQuestionCount(stepId)
                        }
                        examQuestionCountCache[stepId] = size
                        if (bindingAdapterPosition == RecyclerView.NO_POSITION) {
                            return@launch
                        }
                        val adapterPosition = bindingAdapterPosition
                        val currentStepId = getItem(adapterPosition)?.id
                        if (currentStepId == stepId) {
                            rowStepsBinding.tvDescription.text = context.getString(R.string.test_size, size)
                        }
                    }
                }
            }
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

        fun clear() {
            loadJob?.cancel()
            loadJob = null
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

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    companion object {
        private val STEP_ITEM_COMPARATOR = DiffUtils.itemCallback<StepItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
