package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentCourseStepBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.isMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utilities.CustomClickableSpan
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import java.util.Date
import java.util.UUID
import java.util.regex.Pattern

class CourseStepFragment : BaseContainerFragment(), ImageCaptureCallback {
    private lateinit var fragmentCourseStepBinding: FragmentCourseStepBinding
    var stepId: String? = null
    private lateinit var dbService: DatabaseService
    private lateinit var cRealm: Realm
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
        dbService = DatabaseService(requireActivity())
        cRealm = dbService.realmInstance
        user = UserProfileDbHandler(requireContext()).userModel
        fragmentCourseStepBinding.btnTakeTest.visibility = View.VISIBLE
        fragmentCourseStepBinding.btnTakeSurvey.visibility = View.VISIBLE
        return fragmentCourseStepBinding.root
    }

    private fun saveCourseProgress() {
        if (!cRealm.isInTransaction) cRealm.beginTransaction()
        var courseProgress = cRealm.where(RealmCourseProgress::class.java).equalTo("courseId", step.courseId).equalTo("userId", user?.id).equalTo("stepNum", stepNumber).findFirst()
        if (courseProgress == null) {
            courseProgress = cRealm.createObject(RealmCourseProgress::class.java, UUID.randomUUID().toString())
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
        cRealm.commitTransaction()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::cRealm.isInitialized && !cRealm.isClosed) {
            cRealm.close()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        step = cRealm.where(RealmCourseStep::class.java).equalTo("id", stepId).findFirst()!!
        resources = cRealm.where(RealmMyLibrary::class.java).equalTo("stepId", stepId).findAll()
        stepExams = cRealm.where(RealmStepExam::class.java).equalTo("stepId", stepId).equalTo("type", "courses").findAll()
        stepSurvey = cRealm.where(RealmStepExam::class.java).equalTo("stepId", stepId).equalTo("type", "surveys").findAll()
        fragmentCourseStepBinding.btnResources.text = getString(R.string.resources_size, resources.size)
        hideTestIfNoQuestion()
        fragmentCourseStepBinding.tvTitle.text = step.stepTitle
        val markdownContentWithLocalPaths = prependBaseUrlToImages(step.description, "file://${MainApplication.context.getExternalFilesDir(null)}/ole/")
        setMarkdownText(fragmentCourseStepBinding.description, markdownContentWithLocalPaths)
        fragmentCourseStepBinding.description.movementMethod = LinkMovementMethod.getInstance()
        if (!isMyCourse(user?.id, step.courseId, cRealm)) {
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
        if (isVisible && isMyCourse(user?.id, step.courseId, cRealm)) {
            saveCourseProgress()
        }
    }

    private fun hideTestIfNoQuestion() {
        fragmentCourseStepBinding.btnTakeTest.visibility = View.GONE
        fragmentCourseStepBinding.btnTakeSurvey.visibility = View.GONE
        if (stepExams.isNotEmpty()) {
            val firstStepId = stepExams[0].id
            val isTestPersent = existsSubmission(firstStepId, "exam")
            fragmentCourseStepBinding.btnTakeTest.text = if (isTestPersent) { getString(R.string.retake_test, stepExams.size) } else { getString(R.string.take_test, stepExams.size) }
            fragmentCourseStepBinding.btnTakeTest.visibility = View.VISIBLE
        }
        if (stepSurvey.isNotEmpty()) {
            val firstStepId = stepSurvey[0].id
            val isSurveyPresent = existsSubmission(firstStepId, "survey")
            fragmentCourseStepBinding.btnTakeSurvey.text = if (isSurveyPresent) { "redo survey" } else { "record survey" }
            fragmentCourseStepBinding.btnTakeSurvey.visibility = View.VISIBLE
            }
        }

    private fun existsSubmission(firstStepId:String? , submissionType: String): Boolean{
        val questions = cRealm.where(RealmExamQuestion::class.java).equalTo("examId", firstStepId).findAll()
        var isPresent=false
        if (questions != null && questions.size > 0) {
            val examId=questions[0]?.examId
            val isSubmitted = step.courseId?.let { courseId ->
                val parentId = "$examId@$courseId"
                cRealm.where(RealmSubmission::class.java)
                    .equalTo("userId",user?.id)
                    .equalTo("parentId", parentId)
                    .equalTo("type", submissionType)
                    .findFirst() != null
            } ?: false
            isPresent= isSubmitted
        }
        return isPresent
    }

    override fun setMenuVisibility(visible: Boolean) {
        super.setMenuVisibility(visible)
        try {
            if (visible && isMyCourse(user?.id, step.courseId, cRealm)) {
                saveCourseProgress()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setListeners() {
        val notDownloadedResources: List<RealmMyLibrary> = cRealm.where(RealmMyLibrary::class.java).equalTo("stepId", stepId).equalTo("resourceOffline", false).isNotNull("resourceLocalAddress").findAll()
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
                AdapterMySubmission.openSurvey(homeItemClickListener, stepSurvey[0].id, false)
            }
        }
        val downloadedResources: List<RealmMyLibrary> = cRealm.where(RealmMyLibrary::class.java).equalTo("stepId", stepId).equalTo("resourceOffline", true).isNotNull("resourceLocalAddress").findAll()
        setOpenResourceButton(downloadedResources, fragmentCourseStepBinding.btnOpen)
        fragmentCourseStepBinding.btnResources.visibility = View.GONE
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        setListeners()
    }

    override fun onImageCapture(fileUri: String?) {}

    companion object {
        fun prependBaseUrlToImages(markdownContent: String?, baseUrl: String): String {
            val pattern = "!\\[.*?]\\((.*?)\\)"
            val imagePattern = Pattern.compile(pattern)
            val matcher = markdownContent?.let { imagePattern.matcher(it) }
            val result = StringBuffer()
            if (matcher != null) {
                while (matcher.find()) {
                    val relativePath = matcher.group(1)
                    val modifiedPath = relativePath?.replaceFirst("resources/".toRegex(), "")
                    val fullUrl = baseUrl + modifiedPath
                    Log.d("Markdown", "fullUrl: $fullUrl")
                    matcher.appendReplacement(result, "<img src=$fullUrl width=600 height=350/>")
                }
            }
            matcher?.appendTail(result)
            return result.toString()
        }
    }
}
