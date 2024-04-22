package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowStepsBinding
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmStepExam

class AdapterSteps(private val context: Context, private val list: List<RealmCourseStep>, private val realm: Realm) : RecyclerView.Adapter<AdapterSteps.ViewHolder>() {
    private val descriptionVisibilityList: MutableList<Boolean> = ArrayList()
    private var currentlyVisiblePosition = RecyclerView.NO_POSITION

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
            var size = 0
            val exam = realm.where(RealmStepExam::class.java).equalTo("stepId", step.id).findFirst()
            if (exam != null) {
                size = exam.noOfQuestions
            }
            rowStepsBinding.tvDescription.text = "${context.getString(R.string.this_test_has)}$size${context.getString(R.string.questions)}"
            if (descriptionVisibilityList[position]) {
                rowStepsBinding.tvDescription.visibility = View.VISIBLE
            } else {
                rowStepsBinding.tvDescription.visibility = View.GONE
            }
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
}