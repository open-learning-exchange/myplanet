package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentCourseStepBinding
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.isMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utilities.CustomClickableSpan
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class CourseStepFragment : BaseContainerFragment(), ImageCaptureCallback {
    private lateinit var fragmentCourseStepBinding: FragmentCourseStepBinding
    var stepId: String? = null
    private lateinit var step: RealmCourseStep
    private lateinit var resources: List<RealmMyLibrary>
    private lateinit var stepExams: List<RealmStepExam>
    private lateinit var stepSurvey: List<RealmStepExam>
    var user: RealmUserModel? = null
    private var stepNumber = 0
    @Inject
    lateinit var courseRepository: CourseRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            stepId = requireArguments().getString("stepId")
            stepNumber = requireArguments().getInt("stepNumber")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCourseStepBinding = FragmentCourseStepBinding.inflate(inflater, container, false)
        user = profileDbHandler.userModel
        fragmentCourseStepBinding.btnTakeTest.visibility = View.VISIBLE
        fragmentCourseStepBinding.btnTakeSurvey.visibility = View.VISIBLE
        return fragmentCourseStepBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseService.withRealm { realm ->
            step = realm.where(RealmCourseStep::class.java)
                .equalTo("id", stepId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }!!
            resources = realm.where(RealmMyLibrary::class.java)
                .equalTo("stepId", stepId)
                .findAll()
                .let { realm.copyFromRealm(it) }
            stepExams = realm.where(RealmStepExam::class.java)
                .equalTo("stepId", stepId)
                .equalTo("type", "courses")
                .findAll()
                .let { realm.copyFromRealm(it) }
            stepSurvey = realm.where(RealmStepExam::class.java)
                .equalTo("stepId", stepId)
                .equalTo("type", "surveys")
                .findAll()
                .let { realm.copyFromRealm(it) }
        }
        fragmentCourseStepBinding.btnResources.text = getString(R.string.resources_size, resources.size)
        hideTestIfNoQuestion()
        fragmentCourseStepBinding.tvTitle.text = step.stepTitle
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            step.description,
            "file://${MainApplication.context.getExternalFilesDir(null)}/ole/",
            600,
            350
        )
        setMarkdownText(fragmentCourseStepBinding.description, markdownContentWithLocalPaths)
        fragmentCourseStepBinding.description.movementMethod = LinkMovementMethod.getInstance()
        val userHasCourse = databaseService.withRealm { realm ->
            isMyCourse(user?.id, step.courseId, realm)
        }
        if (!userHasCourse) {
            fragmentCourseStepBinding.btnTakeTest.visibility = View.GONE
            fragmentCourseStepBinding.btnTakeSurvey.visibility = View.GONE
        }
        setListeners()
        val textWithSpans = fragmentCourseStepBinding.description.text
        if (textWithSpans is Spannable) {
            val urlSpans = textWithSpans.getSpans(0, textWithSpans.length, URLSpan::class.java)
            for (urlSpan in urlSpans) {
                val start = textWithSpans.getSpanStart(urlSpan)
                val end = textWithSpans.getSpanEnd(urlSpan)
                val dynamicTitle = textWithSpans.subSequence(start, end).toString()
                textWithSpans.setSpan(CustomClickableSpan(urlSpan.url, dynamicTitle, requireActivity()), start, end, textWithSpans.getSpanFlags(urlSpan))
                textWithSpans.removeSpan(urlSpan)
            }
        }
        if (isVisible && userHasCourse) {
            viewLifecycleOwner.lifecycleScope.launch {
                val currentStepId = stepId ?: return@launch
                courseRepository.saveCourseProgress(currentStepId, stepNumber, user, stepExams.isNotEmpty())
            }
        }
    }

    private fun hideTestIfNoQuestion() {
        fragmentCourseStepBinding.btnTakeTest.visibility = View.GONE
        fragmentCourseStepBinding.btnTakeSurvey.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            if (stepExams.isNotEmpty()) {
                val firstStepId = stepExams[0].id
                val isTestPresent = submissionRepository.hasSubmission(firstStepId, step.courseId, user?.id, "exam")
                fragmentCourseStepBinding.btnTakeTest.text = if (isTestPresent) {
                    getString(R.string.retake_test, stepExams.size)
                } else {
                    getString(R.string.take_test, stepExams.size)
                }
                fragmentCourseStepBinding.btnTakeTest.visibility = View.VISIBLE
            }
            if (stepSurvey.isNotEmpty()) {
                val firstStepId = stepSurvey[0].id
                val isSurveyPresent = submissionRepository.hasSubmission(firstStepId, step.courseId, user?.id, "survey")
                fragmentCourseStepBinding.btnTakeSurvey.text = if (isSurveyPresent) {
                    getString(R.string.redo_survey)
                } else {
                    getString(R.string.record_survey)
                }
                fragmentCourseStepBinding.btnTakeSurvey.visibility = View.VISIBLE
            }
        }
    }

    override fun setMenuVisibility(visible: Boolean) {
        super.setMenuVisibility(visible)
        try {
            if (visible) {
                val userHasCourse = databaseService.withRealm { realm ->
                    isMyCourse(user?.id, step.courseId, realm)
                }
                if (userHasCourse) {
                    lifecycleScope.launch {
                        val currentStepId = stepId ?: return@launch
                        val hasExams = if (::stepExams.isInitialized) stepExams.isNotEmpty() else false
                        courseRepository.saveCourseProgress(currentStepId, stepNumber, user, hasExams)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setListeners() {
        val notDownloadedResources: List<RealmMyLibrary> = databaseService.withRealm { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("stepId", stepId)
                .equalTo("resourceOffline", false)
                .isNotNull("resourceLocalAddress")
                .findAll()
                .let { realm.copyFromRealm(it) }
        }
        setResourceButton(notDownloadedResources, fragmentCourseStepBinding.btnResources)
        fragmentCourseStepBinding.btnTakeTest.setOnClickListener {
            if (stepExams.isNotEmpty()) {
                val takeExam: Fragment = TakeExamFragment()
                val b = Bundle()
                b.putString("stepId", stepId)
                b.putInt("stepNum", stepNumber)
                takeExam.arguments = b
                homeItemClickListener?.openCallFragment(takeExam)
                capturePhoto(this)
            }
        }

        fragmentCourseStepBinding.btnTakeSurvey.setOnClickListener {
            if (stepSurvey.isNotEmpty()) {
                AdapterMySubmission.openSurvey(homeItemClickListener, stepSurvey[0].id, false, false, "")
            }
        }
        val downloadedResources: List<RealmMyLibrary> = databaseService.withRealm { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("stepId", stepId)
                .equalTo("resourceOffline", true)
                .isNotNull("resourceLocalAddress")
                .findAll()
                .let { realm.copyFromRealm(it) }
        }
        setOpenResourceButton(downloadedResources, fragmentCourseStepBinding.btnOpen)
        fragmentCourseStepBinding.btnResources.visibility = View.GONE
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        setListeners()
    }

    override fun onImageCapture(fileUri: String?) {}

}
