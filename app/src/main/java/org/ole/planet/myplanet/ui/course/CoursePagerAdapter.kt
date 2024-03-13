package org.ole.planet.myplanet.ui.course

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

class CoursePagerAdapter(fm: FragmentManager, private val courseId: String?, private val steps: Array<String?>) : FragmentStatePagerAdapter(fm) {
    override fun getItem(position: Int): Fragment {
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

    override fun getCount(): Int {
        return steps.size + 1
    }
}
