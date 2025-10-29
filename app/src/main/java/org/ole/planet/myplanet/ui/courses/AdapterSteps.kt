package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.repository.SubmissionRepository

class AdapterSteps(
    private val context: Context,
    private val list: List<RealmCourseStep>,
    private val submissionRepository: SubmissionRepository
) : RecyclerView.Adapter<AdapterSteps.ViewHolder>() {
    private val descriptionVisibilityList: MutableList<Boolean> = MutableList(list.size) { false }
    private var currentlyVisiblePosition = RecyclerView.NO_POSITION
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(job + Dispatchers.Main)
    private val examQuestionCountCache = mutableMapOf<String, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val resources = context.resources
        val cardView = CardView(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { params ->
                val margin = resources.getDimensionPixelSize(R.dimen.padding_small)
                params.setMargins(margin, margin, margin, margin)
            }
            val elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f,
                resources.displayMetrics
            )
            cardElevation = elevation
            useCompatPadding = true
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
        }

        val padding = resources.getDimensionPixelSize(R.dimen.padding_normal)
        val container = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
        }

        val titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.daynight_textColor))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_mid))
            setPadding(0, 0, 0, padding / 2)
        }

        val descriptionView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(ContextCompat.getColor(context, R.color.hint_color))
            visibility = View.GONE
        }

        container.addView(titleView)
        container.addView(descriptionView)
        cardView.addView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        return ViewHolder(cardView, titleView, descriptionView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class ViewHolder(
        root: View,
        private val titleView: TextView,
        private val descriptionView: TextView
    ) : RecyclerView.ViewHolder(root) {
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
            titleView.text = step.stepTitle
            descriptionView.text = context.getString(R.string.test_size, 0)
            loadJob?.cancel()

            val stepId = step.id
            if (!stepId.isNullOrEmpty()) {
                val cachedCount = examQuestionCountCache[stepId]
                if (cachedCount != null) {
                    descriptionView.text = context.getString(R.string.test_size, cachedCount)
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
                            descriptionView.text = context.getString(R.string.test_size, size)
                        }
                    }
                }
            }
            if (descriptionVisibilityList[position]) {
                descriptionView.visibility = View.VISIBLE
            } else {
                descriptionView.visibility = View.GONE
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
