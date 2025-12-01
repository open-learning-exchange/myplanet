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
                    val list = jsonArray.map { it.asJsonObject }
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
