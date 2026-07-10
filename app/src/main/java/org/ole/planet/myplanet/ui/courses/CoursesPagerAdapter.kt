package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter

class CoursesPagerAdapter(fm: Fragment, private val courseId: String?) : FragmentStateAdapter(fm) {
    private val steps = mutableListOf<String>()
    private val itemIds = mutableMapOf<String, Long>()
    private var nextId = 1L

    companion object {
        private const val COURSE_DETAIL_ID = 0L
    }

    fun submitList(newSteps: List<String>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = steps.size + 1 // +1 for the course detail fragment
            override fun getNewListSize(): Int = newSteps.size + 1

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                if (oldItemPosition == 0 && newItemPosition == 0) return true
                if (oldItemPosition == 0 || newItemPosition == 0) return false
                return steps[oldItemPosition - 1] == newSteps[newItemPosition - 1]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                if (oldItemPosition == 0 && newItemPosition == 0) return true
                if (oldItemPosition == 0 || newItemPosition == 0) return false
                return steps[oldItemPosition - 1] == newSteps[newItemPosition - 1]
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)

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
