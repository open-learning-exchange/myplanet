package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.databinding.FragmentCoursesProgressBinding
import org.ole.planet.myplanet.model.CoursesProgressRow
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class CoursesProgressFragment : Fragment() {
    private var _binding: FragmentCoursesProgressBinding? = null
    private val binding get() = _binding!!
    private val progressViewModel: ProgressViewModel by viewModels()
    private lateinit var progressAdapter: CoursesProgressAdapter

    @Inject
    lateinit var gson: Gson

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCoursesProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressAdapter = CoursesProgressAdapter(requireActivity())
        binding.rvMyprogress.layoutManager = LinearLayoutManager(requireActivity())
        binding.rvMyprogress.adapter = progressAdapter
        progressViewModel.loadCourseData()
        observeCourseData()
    }

    private fun observeCourseData() {
        collectWhenStarted(progressViewModel.courseData) { courseData ->
            courseData?.let { jsonArray ->
                val type = object : TypeToken<Map<String, Int>>() {}.type
                val list = jsonArray.map { element ->
                    val obj = element.asJsonObject

                    val stepMistake = if (obj.has("stepMistake")) {
                        gson.fromJson<Map<String, Int>>(obj.get("stepMistake"), type)
                    } else {
                        null
                    }

                    CoursesProgressRow(
                        courseId = obj.get("courseId").asString,
                        courseName = obj.get("courseName").asString,
                        progressCurrent = obj.getAsJsonObject("progress")?.get("current")?.asInt,
                        progressMax = obj.getAsJsonObject("progress")?.get("max")?.asInt,
                        mistakes = obj.get("mistakes")?.asInt,
                        stepMistake = stepMistake
                    )
                }
                progressAdapter.submitList(list)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
