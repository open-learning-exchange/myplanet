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
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentCourseStepBinding
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.isMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utilities.CustomClickableSpan
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

private data class StepData(
    val step: RealmCourseStep,
    val resources: List<RealmMyLibrary>,
    val stepExams: List<RealmStepExam>,
    val stepSurvey: List<RealmStepExam>,
    val userHasCourse: Boolean
)

class CourseStepFragment : BaseContainerFragment(), ImageCaptureCallback {
    private lateinit var fragmentCourseStepBinding: FragmentCourseStepBinding
    var stepId: String? = null
    private lateinit var step: RealmCourseStep
    private lateinit var resources: List<RealmMyLibrary>
    private lateinit var stepExams: List<RealmStepExam>
    private lateinit var stepSurvey: List<RealmStepExam>
    var user: RealmUserModel? = null
    private var stepNumber = 0
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

    private fun saveCourseProgress() {
        databaseService.withRealm { realm ->
            if (!realm.isInTransaction) realm.beginTransaction()
            var courseProgress = realm.where(RealmCourseProgress::class.java)
                .equalTo("courseId", step.courseId)
                .equalTo("userId", user?.id)
                .equalTo("stepNum", stepNumber)
                .findFirst()
            if (courseProgress == null) {
                courseProgress = realm.createObject(RealmCourseProgress::class.java, UUID.randomUUID().toString())
                courseProgress.createdDate = Date().time
            }
            courseProgress?.courseId = step.courseId
            courseProgress?.stepNum = stepNumber
            if (stepExams.isEmpty()) {
                courseProgress?.passed = true
            }
            courseProgress?.createdOn = user?.planetCode
            courseProgress?.updatedDate = Date().time
            courseProgress?.parentCode = user?.parentCode
            courseProgress?.userId = user?.id
            realm.commitTransaction()
        }
    }
    private suspend fun loadStepData(): StepData {
        return databaseService.withRealmAsync { realm ->
            val step = realm.where(RealmCourseStep::class.java)
                .equalTo("id", stepId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }!!

            val resources = realm.where(RealmMyLibrary::class.java)
                .equalTo("stepId", stepId)
                .findAll()
                .let { realm.copyFromRealm(it) }

            val stepExams = realm.where(RealmStepExam::class.java)
                .equalTo("stepId", stepId)
                .equalTo("type", "courses")
                .findAll()
                .let { realm.copyFromRealm(it) }

            val stepSurvey = realm.where(RealmStepExam::class.java)
                .equalTo("stepId", stepId)
                .equalTo("type", "surveys")
                .findAll()
                .let { realm.copyFromRealm(it) }
            val userHasCourse = isMyCourse(user?.id, step.courseId, realm)
            StepData(step, resources, stepExams, stepSurvey, userHasCourse)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            fragmentCourseStepBinding.progressBar.visibility = View.VISIBLE
            try {
                val stepData = loadStepData()
                step = stepData.step
                resources = stepData.resources
                stepExams = stepData.stepExams
                stepSurvey = stepData.stepSurvey
                renderStepData(stepData.userHasCourse)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                fragmentCourseStepBinding.progressBar.visibility = View.GONE
            }
        }
    }
    private fun renderStepData(userHasCourse: Boolean) {
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
            saveCourseProgress()
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
                    saveCourseProgress()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            val notDownloadedResources = libraryRepository.getStepResources(stepId, resourceOffline = false)
            setResourceButton(notDownloadedResources, fragmentCourseStepBinding.btnResources)
            val downloadedResources = libraryRepository.getStepResources(stepId, resourceOffline = true)
            setOpenResourceButton(downloadedResources, fragmentCourseStepBinding.btnOpen)
        }
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
        fragmentCourseStepBinding.btnResources.visibility = View.GONE
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        setListeners()
    }

    override fun onImageCapture(fileUri: String?) {}

}
