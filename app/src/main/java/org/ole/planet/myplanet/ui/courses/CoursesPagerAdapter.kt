package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.utils.DiffUtils

class CoursesPagerAdapter(fm: Fragment, private val courseId: String?) : FragmentStateAdapter(fm) {
    private val steps = mutableListOf<String>()
    private val itemIds = mutableMapOf<String, Long>()
    private var nextId = 1L

    companion object {
        private const val COURSE_DETAIL_ID = 0L
    }

    private sealed interface StepItem
    private object HeaderItem : StepItem
    private data class StepEntry(val id: String) : StepItem

    fun submitList(newSteps: List<String>) {
        val oldWithHeader: List<StepItem> = listOf(HeaderItem) + steps.map(::StepEntry)
        val newWithHeader: List<StepItem> = listOf(HeaderItem) + newSteps.map(::StepEntry)

        val diffResult = DiffUtils.calculateDiff(
            oldWithHeader,
            newWithHeader,
            areItemsTheSame = { a, b -> a == b },
            areContentsTheSame = { a, b -> a == b }
        )

        newSteps.forEach { stepId ->
            if (!itemIds.containsKey(stepId)) {
                itemIds[stepId] = nextId++
            }
        }

        steps.clear()
        steps.addAll(newSteps)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun createFragment(position: Int): Fragment {
        val b = Bundle()
        val f: Fragment
        if (position == 0) {
            f = CourseDetailFragment()
            b.putString("courseId", courseId)
        } else {
            f = CourseStepFragment()
            b.putString("stepId", steps[position - 1])
            b.putInt("stepNumber", position)
            if (position < steps.size) {
                b.putString("nextStepId", steps[position])
            }
        }
        f.arguments = b
        return f
    }

    override fun getItemCount(): Int {
        return steps.size + 1
    }

    override fun getItemId(position: Int): Long {
        return if (position == 0) {
            COURSE_DETAIL_ID
        } else {
            itemIds[steps[position - 1]] ?: RecyclerView.NO_ID
        }
    }

    override fun containsItem(itemId: Long): Boolean {
        if (itemId == COURSE_DETAIL_ID) return true
        return steps.any { itemIds[it] == itemId }
    }
}
