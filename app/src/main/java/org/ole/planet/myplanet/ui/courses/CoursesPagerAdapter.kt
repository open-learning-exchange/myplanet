package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class CoursesPagerAdapter(fm: Fragment, private val courseId: String?, private val steps: Array<String?>) : FragmentStateAdapter(fm) {
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
        }
        f.arguments = b
        return f
    }

    override fun getItemCount(): Int {
        return steps.size + 1
    }
}
