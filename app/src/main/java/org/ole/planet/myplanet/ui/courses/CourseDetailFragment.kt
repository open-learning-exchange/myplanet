package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.utils.MarkdownUtils.prependBaseUrlToImages
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class CourseDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!
    private var id: String? = null
    private val viewModel: CourseDetailViewModel by viewModels()
    private var isRatingViewInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            id = requireArguments().getString("courseId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        id?.let {
            viewModel.loadCourseDetail(it)
        }

        collectWhenStarted(viewModel.uiState) { state ->
            when (state) {
                is CourseDetailUiState.Loading -> {
                    // Show loading indicator if needed
                }
                is CourseDetailUiState.Success -> {
                    bindCourseData(state)
                }
                is CourseDetailUiState.Error -> {
                    context?.let { ctx ->
                        android.widget.Toast.makeText(ctx, state.message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun bindCourseData(state: CourseDetailUiState.Success) {
        val course = state.course
        setTextViewVisibility(binding.subjectLevel, course.subjectLevel, binding.ltSubjectLevel)
        setTextViewVisibility(binding.method, course.method, binding.ltMethod)
        setTextViewVisibility(binding.gradeLevel, course.gradeLevel, binding.ltGradeLevel)
        setTextViewVisibility(binding.language, course.languageOfInstruction, binding.ltLanguage)

        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            course.description,
            "file://" + MainApplication.context.getExternalFilesDir(null) + "/ole/",
            600,
            350
        )
        setMarkdownText(binding.description, markdownContentWithLocalPaths)

        binding.noOfExams.text = context?.getString(
            R.string.number_placeholder,
            state.examCount
        )

        setResourceButton(state.resources, binding.btnResources)
        setOpenResourceButton(state.downloadedResources, binding.btnOpen)
        setStepsList(state.stepItems)

        if (!isRatingViewInitialized) {
            initRatingView("course", course.courseId, course.courseTitle, this@CourseDetailFragment)
            isRatingViewInitialized = true
        }
        setRatings(state.ratingSummary)
    }

    private fun setTextViewVisibility(textView: TextView, content: String?, layout: View) {
        if (content?.isEmpty() == true) {
            layout.visibility = View.GONE
        } else {
            layout.visibility = View.VISIBLE
            textView.text = content
        }
    }

    private fun setStepsList(steps: List<StepItem>) {
        binding.stepsList.layoutManager = LinearLayoutManager(activity)
        val adapter = CoursesStepsAdapter(requireActivity())
        binding.stepsList.adapter = adapter
        adapter.submitList(steps)
    }

    override fun onRatingChanged() {
        id?.let {
            viewModel.refreshRatings(it)
        }
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        id?.let {
            viewModel.loadCourseDetail(it)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
