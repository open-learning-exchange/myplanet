package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentMyProgressBinding
import org.ole.planet.myplanet.model.ProgressData

@AndroidEntryPoint
class MyProgressFragment : Fragment() {
    private var _binding: FragmentMyProgressBinding? = null
    private val binding get() = _binding!!
    private val progressViewModel: ProgressViewModel by viewModels()
    private lateinit var myProgressAdapter: AdapterMyProgress

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        myProgressAdapter = AdapterMyProgress(requireActivity())
        binding.rvMyprogress.layoutManager = LinearLayoutManager(requireActivity())
        binding.rvMyprogress.adapter = myProgressAdapter
        progressViewModel.loadCourseData()
        observeCourseData()
    }

    private fun observeCourseData() {
        lifecycleScope.launch {
            progressViewModel.courseData.collect { courseData ->
                courseData?.let { jsonArray ->
                    val list = jsonArray.map {
                        val course = it.asJsonObject
                        val courseId = course.get("courseId").asString
                        val courseName = course.get("courseName").asString
                        val progressObject = if (course.has("progress")) course.get("progress").asJsonObject else null
                        val current = progressObject?.get("current")?.asInt ?: 0
                        val max = progressObject?.get("max")?.asInt ?: 1
                        val progress = if (max > 0) (current * 100 / max) else 0
                        val mistakes = if (course.has("mistakes")) course.get("mistakes").asString.toIntOrNull() ?: 0 else 0
                        val stepMistakeObject = if (course.has("stepMistake")) course.get("stepMistake").asJsonObject else null
                        val stepMistakes = stepMistakeObject?.entrySet()?.associate { (key, value) ->
                            key to value.asInt
                        } ?: emptyMap()
                        ProgressData(courseId, courseName, progress, mistakes, stepMistakes)
                    }
                    myProgressAdapter.submitList(list)
                }
            }
        }
    }

    companion object {
        fun getCourseProgress(courseData: JsonArray, courseId: String): JsonObject? {
            courseData.forEach { element ->
                val course = element.asJsonObject
                if (course.get("courseId").asString == courseId) {
                    return course.getAsJsonObject("progress")
                }
            }
            return null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
