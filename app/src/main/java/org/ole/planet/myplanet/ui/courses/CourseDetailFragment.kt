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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
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
    lateinit var courseRepository: CourseRepository
    @Inject
    lateinit var ratingRepository: RatingRepository
    private var courseOnlineResources: List<RealmMyLibrary> = emptyList()
    private var courseOfflineResources: List<RealmMyLibrary> = emptyList()
    private var courseSteps: List<RealmCourseStep> = emptyList()
    private var courseExamCount: Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            id = requireArguments().getString("courseId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        user = UserProfileDbHandler(requireContext()).userModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            loadCourseDetails()
        }
    }

    private suspend fun loadCourseDetails() {
        val courseId = id?.takeIf { it.isNotBlank() }
        courseOnlineResources = emptyList()
        courseOfflineResources = emptyList()
        courseSteps = emptyList()
        courseExamCount = 0
        if (courseId.isNullOrBlank()) {
            courses = null
            initRatingView("course", null, null, this@CourseDetailFragment)
            renderCourseDetails(null)
            setRatings(null)
            return
        }
        val course = courseRepository.getCourseById(courseId)
        courses = course
        initRatingView("course", course?.courseId ?: courseId, course?.courseTitle, this@CourseDetailFragment)
        if (course == null) {
            renderCourseDetails(null)
            setRatings(null)
            return
        }
        val effectiveCourseId = course.courseId?.takeIf { it.isNotBlank() } ?: courseId
        courseExamCount = courseRepository.getCourseExamCount(effectiveCourseId)
        coroutineScope {
            val onlineResourcesDeferred = async {
                libraryRepository.getCourseOnlineResources(effectiveCourseId)
            }
            val offlineResourcesDeferred = async {
                libraryRepository.getCourseOfflineResources(effectiveCourseId)
            }
            val stepsDeferred = async { courseRepository.getCourseSteps(effectiveCourseId) }
            courseOnlineResources = onlineResourcesDeferred.await()
            courseOfflineResources = offlineResourcesDeferred.await()
            courseSteps = stepsDeferred.await()
        }
        renderCourseDetails(course)
        refreshRatings()
    }

    private fun renderCourseDetails(course: RealmMyCourse?) {
        if (course == null) {
            setTextViewVisibility(binding.subjectLevel, null, binding.ltSubjectLevel)
            setTextViewVisibility(binding.method, null, binding.ltMethod)
            setTextViewVisibility(binding.gradeLevel, null, binding.ltGradeLevel)
            setTextViewVisibility(binding.language, null, binding.ltLanguage)
            setMarkdownText(binding.description, "")
            binding.noOfExams.text = context?.getString(R.string.number_placeholder, 0)
            setResourceButton(courseOnlineResources, binding.btnResources)
            setOpenResourceButton(courseOfflineResources, binding.btnOpen)
            setStepsList(courseSteps)
            return
        }
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
            courseExamCount
        )
        setResourceButton(courseOnlineResources, binding.btnResources)
        setOpenResourceButton(courseOfflineResources, binding.btnOpen)
        setStepsList(courseSteps)
    }

    private fun setTextViewVisibility(textView: TextView, content: String?, layout: View) {
        if (content.isNullOrEmpty()) {
            layout.visibility = View.GONE
            textView.text = ""
        } else {
            layout.visibility = View.VISIBLE
            textView.text = content
        }
    }

    private fun setStepsList(steps: List<RealmCourseStep>) {
        binding.stepsList.layoutManager = LinearLayoutManager(activity)
        binding.stepsList.adapter = AdapterSteps(requireActivity(), steps, submissionRepository)
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
            val ratingSummary = ratingRepository.getRatingSummary("course", courseId, userId)
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
            loadCourseDetails()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
