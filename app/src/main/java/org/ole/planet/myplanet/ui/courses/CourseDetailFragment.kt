package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

@AndroidEntryPoint
class CourseDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!
    var courses: RealmMyCourse? = null
    var user: RealmUserModel? = null
    var id: String? = null
    @Inject
    lateinit var ratingsRepository: RatingsRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            id = requireArguments().getString("courseId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        user = profileDbHandler?.userModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            courses = id?.takeIf { it.isNotBlank() }?.let { coursesRepository.getCourseByCourseId(it) }
            initRatingView("course", id ?: courses?.courseId, courses?.courseTitle, this@CourseDetailFragment)
            courses?.let { bindCourseData(it) }
        }
    }

    private suspend fun bindCourseData(course: RealmMyCourse) {
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
        val courseId = course.courseId
        val examCount = coursesRepository.getCourseExamCount(courseId)
        binding.noOfExams.text = context?.getString(
            R.string.number_placeholder,
            examCount
        )
        val resources = coursesRepository.getCourseOnlineResources(courseId)
        setResourceButton(resources, binding.btnResources)
        val downloadedResources = coursesRepository.getCourseOfflineResources(courseId)
        setOpenResourceButton(downloadedResources, binding.btnOpen)
        val steps = coursesRepository.getCourseSteps(courseId)
        setStepsList(steps)
        refreshRatings()
    }

    private fun setTextViewVisibility(textView: TextView, content: String?, layout: View) {
        if (content?.isEmpty() == true) {
            layout.visibility = View.GONE
        } else {
            textView.text = content
        }
    }

    private fun setStepsList(steps: List<RealmCourseStep>) {
        binding.stepsList.layoutManager = LinearLayoutManager(activity)
        val adapter = StepsAdapter(requireActivity(), submissionRepository, viewLifecycleOwner)
        binding.stepsList.adapter = adapter
        adapter.submitList(steps.map { step ->
            StepItem(
                id = step.id,
                stepTitle = step.stepTitle
            )
        })
    }

    override fun onRatingChanged() {
        viewLifecycleOwner.lifecycleScope.launch {
            refreshRatings()
        }
    }

    private suspend fun refreshRatings() {
        val courseId = courses?.courseId
        val userId = user?.id
        if (courseId != null && userId != null) {
            val ratingSummary = ratingsRepository.getRatingSummary("course", courseId, userId)
            val jsonObject = com.google.gson.JsonObject().apply {
                addProperty("averageRating", ratingSummary.averageRating)
                addProperty("total", ratingSummary.totalRatings)
                ratingSummary.userRating?.let { addProperty("userRating", it) }
            }
            setRatings(jsonObject)
        } else {
            setRatings(null)
        }
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        viewLifecycleOwner.lifecycleScope.launch {
            courses = id?.takeIf { it.isNotBlank() }?.let { coursesRepository.getCourseByCourseId(it) } ?: courses
            courses?.let { bindCourseData(it) }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
