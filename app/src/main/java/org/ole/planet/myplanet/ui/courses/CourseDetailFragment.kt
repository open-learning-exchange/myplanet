package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.signature.ObjectKey
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class CourseDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!
    private var id: String? = null
    private val viewModel: CourseDetailViewModel by viewModels()
    private var isRatingViewInitialized = false
    private var stepsAdapter: CoursesStepsAdapter? = null

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
                        Toast.makeText(ctx, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        collectWhenStarted(viewModel.stepItems) { steps ->
            setStepsList(steps)
        }
    }

    private fun bindCourseData(state: CourseDetailUiState.Success) {
        val course = state.course
        setTextViewVisibility(binding.subjectLevel, course.subjectLevel, binding.ltSubjectLevel)
        setTextViewVisibility(binding.method, course.method, binding.ltMethod)
        setTextViewVisibility(binding.gradeLevel, course.gradeLevel, binding.ltGradeLevel)
        setTextViewVisibility(binding.language, course.languageOfInstruction, binding.ltLanguage)
        setCourseCover(course.courseId, course.coverFileName, course.courseRev)

        setMarkdownText(binding.description, state.markdownDescription)

        binding.noOfExams.text = context?.getString(
            R.string.number_placeholder,
            state.examCount
        )

        setResourceButton(state.resources, binding.btnResources)
        setOpenResourceButton(state.downloadedResources, binding.btnOpen)

        if (!isRatingViewInitialized) {
            initRatingView("course", course.courseId, course.courseTitle, this@CourseDetailFragment)
            isRatingViewInitialized = true
        }
        setRatings(state.ratingSummary)
    }

    private fun setCourseCover(courseId: String?, coverFileName: String?, courseRev: String?) {
        val coverFile = RealmMyCourse.getCoverImageFile(binding.courseCover.context, courseId, coverFileName)
        val model: Any? = if (coverFile?.exists() == true) {
            coverFile
        } else {
            UrlUtils.getCourseImageUrl(courseId, coverFileName)?.let { url ->
                GlideUrl(url, LazyHeaders.Builder().addHeader("Authorization", UrlUtils.header).build())
            }
        }
        if (model == null) {
            binding.courseCover.visibility = View.GONE
            return
        }
        binding.courseCover.visibility = View.VISIBLE
        Glide.with(binding.courseCover.context)
            .load(model)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(ObjectKey(courseRev ?: ""))
            .error(R.drawable.ole_logo)
            .into(binding.courseCover)
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
        if (stepsAdapter == null) {
            binding.stepsList.layoutManager = LinearLayoutManager(activity)
            stepsAdapter = CoursesStepsAdapter(requireActivity()) { stepId ->
                viewModel.toggleStepDescription(stepId)
            }
            binding.stepsList.adapter = stepsAdapter
        }
        stepsAdapter?.submitList(steps)
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
        stepsAdapter = null
        super.onDestroyView()
    }
}
