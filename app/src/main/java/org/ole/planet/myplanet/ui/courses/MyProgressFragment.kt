package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.databinding.FragmentMyProgressBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.service.UserProfileDbHandler

@AndroidEntryPoint
class MyProgressFragment : Fragment() {
    private var _binding: FragmentMyProgressBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CourseProgressViewModel by viewModels()
    @Inject
    lateinit var databaseService: DatabaseService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val user = UserProfileDbHandler(requireActivity()).userModel
        viewModel.getCourseProgress(user?.id)
        viewModel.courseProgress.observe(viewLifecycleOwner) { courseData ->
            binding.rvMyprogress.layoutManager = LinearLayoutManager(requireActivity())
            binding.rvMyprogress.adapter = AdapterMyProgress(requireActivity(), courseData)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
