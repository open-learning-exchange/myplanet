package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter

class CoursesPagerAdapter(fm: Fragment, private val courseId: String?) : FragmentStateAdapter(fm) {
    private val itemIds = mutableMapOf<String, Long>()
    private var nextId = 1L

    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<String?>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    })

    companion object {
        private const val COURSE_DETAIL_ID = 0L
    }

    fun submitList(newSteps: List<String>) {
        newSteps.forEach { stepId ->
            if (!itemIds.containsKey(stepId)) {
                itemIds[stepId] = nextId++
            }
        }

        differ.submitList(listOf(null) + newSteps)
    }

    override fun createFragment(position: Int): Fragment {
        val b = Bundle()
        val f: Fragment
        val currentList = differ.currentList
        if (position == 0) {
            f = CourseDetailFragment()
            b.putString("courseId", courseId)
        } else {
            f = CourseStepFragment()
            b.putString("stepId", currentList[position])
            b.putInt("stepNumber", position)
            if (position + 1 < currentList.size) {
                b.putString("nextStepId", currentList[position + 1])
            }
        }
        f.arguments = b
        return f
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun getItemId(position: Int): Long {
        if (position == 0) return COURSE_DETAIL_ID
        return differ.currentList[position]?.let { itemIds[it] } ?: RecyclerView.NO_ID
    }

    override fun containsItem(itemId: Long): Boolean {
        if (itemId == COURSE_DETAIL_ID) return true
        val currentList = differ.currentList
        for (i in 1 until currentList.size) {
            val stepId = currentList[i]
            if (stepId != null && itemIds[stepId] == itemId) {
                return true
            }
        }
        return false
    }
}
