package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowStepsBinding
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.repository.SubmissionRepository

class AdapterSteps(
    private val context: Context,
    private val list: List<RealmCourseStep>,
    private val submissionRepository: SubmissionRepository
) : RecyclerView.Adapter<AdapterSteps.ViewHolder>() {
    private val descriptionVisibilityList: MutableList<Boolean> = ArrayList()
    private var currentlyVisiblePosition = RecyclerView.NO_POSITION
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(job + Dispatchers.Main)
    private val examQuestionCountCache = mutableMapOf<String, Int>()

    init {
        for (i in list.indices) {
            descriptionVisibilityList.add(false)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val rowStepsBinding = RowStepsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(rowStepsBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class ViewHolder(private val rowStepsBinding: RowStepsBinding) : RecyclerView.ViewHolder(rowStepsBinding.root) {
        private var loadJob: Job? = null

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    toggleDescriptionVisibility(position)
                }
            }
        }

        fun bind(position: Int) {
            val step = list[position]
            rowStepsBinding.tvTitle.text = step.stepTitle
            rowStepsBinding.tvDescription.text = context.getString(R.string.test_size, 0)
            loadJob?.cancel()

            val stepId = step.id
            if (!stepId.isNullOrEmpty()) {
                val cachedCount = examQuestionCountCache[stepId]
                if (cachedCount != null) {
                    rowStepsBinding.tvDescription.text = context.getString(R.string.test_size, cachedCount)
                } else {
                    val currentPosition = position
                    loadJob = coroutineScope.launch {
                        val size = submissionRepository.getExamQuestionCount(stepId)
                        examQuestionCountCache[stepId] = size
                        if (bindingAdapterPosition == RecyclerView.NO_POSITION) {
                            return@launch
                        }
                        val adapterPosition = bindingAdapterPosition
                        val currentStepId = list.getOrNull(adapterPosition)?.id
                        if (currentStepId == stepId && currentPosition == adapterPosition) {
                            rowStepsBinding.tvDescription.text = context.getString(R.string.test_size, size)
                        }
                    }
                }
            }
            if (descriptionVisibilityList[position]) {
                rowStepsBinding.tvDescription.visibility = View.VISIBLE
            } else {
                rowStepsBinding.tvDescription.visibility = View.GONE
            }
        }

        fun clear() {
            loadJob?.cancel()
            loadJob = null
        }
    }

    private fun toggleDescriptionVisibility(position: Int) {
        if (currentlyVisiblePosition != RecyclerView.NO_POSITION) {
            descriptionVisibilityList[currentlyVisiblePosition] = false
            notifyItemChanged(currentlyVisiblePosition)
        }
        descriptionVisibilityList[position] = !descriptionVisibilityList[position]
        notifyItemChanged(position)
        currentlyVisiblePosition = if (descriptionVisibilityList[position]) position else RecyclerView.NO_POSITION
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        job.cancelChildren()
    }

    fun clearExamQuestionCountCache() {
        examQuestionCountCache.clear()
    }
}
