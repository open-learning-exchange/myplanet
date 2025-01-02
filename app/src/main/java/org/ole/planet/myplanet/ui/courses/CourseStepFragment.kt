package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.*
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.*
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentCourseStepBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.utilities.CameraUtils.ImageCaptureCallback
import org.ole.planet.myplanet.utilities.CameraUtils.capturePhoto
import org.ole.planet.myplanet.utilities.CustomClickableSpan
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import java.util.*
import java.util.regex.Pattern

class CourseStepFragment : BaseContainerFragment(), ImageCaptureCallback {
    private lateinit var fragmentCourseStepBinding: FragmentCourseStepBinding
    var stepId: String? = null
    private lateinit var dbService: DatabaseService
    private lateinit var cRealm: Realm
    private lateinit var step: RealmCourseStep
    private lateinit var resources: RealmResults<RealmMyLibrary>
    private lateinit var stepExams: RealmResults<RealmStepExam>
    private lateinit var stepSurvey: RealmResults<RealmStepExam>
    var user: RealmUserModel? = null
    private var stepNumber = 0
    private val scope = MainApplication.applicationScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            stepId = it.getString("stepId")
            stepNumber = it.getInt("stepNumber")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCourseStepBinding = FragmentCourseStepBinding.inflate(inflater, container, false)
        dbService = DatabaseService()
        cRealm = dbService.realmInstance
        user = UserProfileDbHandler(requireContext()).userModel
        fragmentCourseStepBinding.apply {
            btnTakeTest.visibility = View.VISIBLE
            btnTakeSurvey.visibility = View.VISIBLE
        }
        return fragmentCourseStepBinding.root
    }

    private suspend fun saveCourseProgress() {
        cRealm.write {
            val courseProgress = this.query<RealmCourseProgress>(RealmCourseProgress::class,
                "courseId == $0 AND userId == $1 AND stepNum == $2", step.courseId, user?.id ?: "",
                stepNumber).first().find() ?: RealmCourseProgress().apply {
                    id = UUID.randomUUID().toString()
                    createdDate = Date().time
                }

            copyToRealm(courseProgress.apply {
                this.courseId = step.courseId ?: ""
                this.stepNum = stepNumber
                passed = stepExams.isEmpty()
                createdOn = user?.planetCode ?: ""
                updatedDate = Date().time
                parentCode = user?.parentCode ?: ""
                userId = user?.id ?: ""
            })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        step = cRealm.query<RealmCourseStep>(RealmCourseStep::class, "id == $0", stepId).first().find()!!
        resources = cRealm.query<RealmMyLibrary>(RealmMyLibrary::class, "stepId == $0", stepId).find()
        stepExams = cRealm.query<RealmStepExam>(RealmStepExam::class, "stepId == $0 AND type == $1", stepId, "courses").find()
        stepSurvey = cRealm.query<RealmStepExam>(RealmStepExam::class, "stepId == $0 AND type == $1", stepId, "surveys").find()

        fragmentCourseStepBinding.apply {
            btnResources.text = getString(R.string.resources_size, resources.size)
            hideTestIfNoQuestion()
            tvTitle.text = step.stepTitle

            val markdownContentWithLocalPaths = prependBaseUrlToImages(step.description, "file://${MainApplication.context.getExternalFilesDir(null)}/ole/")
            setMarkdownText(description, markdownContentWithLocalPaths)
            description.movementMethod = LinkMovementMethod.getInstance()

            if (!RealmMyCourse.isMyCourse(user?.id, step.courseId, cRealm)) {
                btnTakeTest.visibility = View.GONE
                btnTakeSurvey.visibility = View.GONE
            }
        }

        setListeners()
        handleMarkdownLinks()

        if (isVisible && RealmMyCourse.isMyCourse(user?.id, step.courseId, cRealm)) {
            scope.launch {
                saveCourseProgress()
            }
        }
    }

    private fun handleMarkdownLinks() {
        val textWithSpans = fragmentCourseStepBinding.description.text as? Spannable ?: return
        val urlSpans = textWithSpans.getSpans(0, textWithSpans.length, URLSpan::class.java)

        urlSpans.forEach { urlSpan ->
            val start = textWithSpans.getSpanStart(urlSpan)
            val end = textWithSpans.getSpanEnd(urlSpan)
            val dynamicTitle = textWithSpans.subSequence(start, end).toString()

            textWithSpans.apply {
                setSpan(CustomClickableSpan(urlSpan.url, dynamicTitle, requireActivity()), start, end, getSpanFlags(urlSpan))
                removeSpan(urlSpan)
            }
        }
    }

    private fun hideTestIfNoQuestion() {
        fragmentCourseStepBinding.apply {
            btnTakeTest.visibility = View.GONE
            btnTakeSurvey.visibility = View.GONE

            if (stepExams.isNotEmpty()) {
                val firstStepId = stepExams[0].id
                val isTestPresent = existsSubmission(firstStepId, "exam")
                btnTakeTest.text = if (isTestPresent) {
                    getString(R.string.retake_test, stepExams.size)
                } else {
                    getString(R.string.take_test, stepExams.size)
                }
                btnTakeTest.visibility = View.VISIBLE
            }

            if (stepSurvey.isNotEmpty()) {
                val firstStepId = stepSurvey[0].id
                val isSurveyPresent = existsSubmission(firstStepId, "survey")
                btnTakeSurvey.text = if (isSurveyPresent) "redo survey" else "record survey"
                btnTakeSurvey.visibility = View.VISIBLE
            }
        }
    }

    private fun existsSubmission(firstStepId: String?, submissionType: String): Boolean {
        val questions = cRealm.query<RealmExamQuestion>(RealmExamQuestion::class, "examId == $0", firstStepId).find()

        if (questions.isEmpty()) return false

        val examId = questions[0].examId
        return step.courseId?.let { courseId ->
            val parentId = "$examId@$courseId"
            cRealm.query<RealmSubmission>(RealmSubmission::class,
                "userId == $0 AND parentId == $1 AND type == $2", user?.id ?: "", parentId, submissionType
            ).first().find() != null
        } == true
    }

    override fun setMenuVisibility(visible: Boolean) {
        super.setMenuVisibility(visible)
        try {
            if (visible && RealmMyCourse.isMyCourse(user?.id, step.courseId, cRealm)) {
                scope.launch {
                    saveCourseProgress()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setListeners() {
        fragmentCourseStepBinding.apply {
            val notDownloadedResources = cRealm.query<RealmMyLibrary>(RealmMyLibrary::class, "stepId == $0 AND resourceOffline == false AND resourceLocalAddress != null", stepId).find()
            setResourceButton(notDownloadedResources, btnResources)

            btnTakeTest.setOnClickListener {
                if (stepExams.isNotEmpty()) {
                    homeItemClickListener?.openCallFragment(TakeExamFragment().apply {
                        arguments = Bundle().apply {
                            putString("stepId", stepId)
                            putInt("stepNum", stepNumber)
                        }
                    })
                    capturePhoto(this@CourseStepFragment)
                }
            }

            btnTakeSurvey.setOnClickListener {
                if (stepSurvey.isNotEmpty()) {
                    AdapterMySubmission.openSurvey(homeItemClickListener, stepSurvey[0].id, false)
                }
            }

            val downloadedResources = cRealm.query<RealmMyLibrary>(RealmMyLibrary::class, "stepId == $0 AND resourceOffline == true AND resourceLocalAddress != null", stepId).find()
            setOpenResourceButton(downloadedResources, btnOpen)
            btnResources.visibility = View.GONE
        }
    }

    override fun onImageCapture(fileUri: String?) {}

    companion object {
        fun prependBaseUrlToImages(markdownContent: String?, baseUrl: String): String {
            val pattern = "!\\[.*?]\\((.*?)\\)"
            val imagePattern = Pattern.compile(pattern)
            return markdownContent?.let { content ->
                val matcher = imagePattern.matcher(content)
                val result = StringBuffer()
                while (matcher.find()) {
                    val relativePath = matcher.group(1)
                    val modifiedPath = relativePath?.replaceFirst("resources/".toRegex(), "")
                    val fullUrl = baseUrl + modifiedPath
                    matcher.appendReplacement(result, "<img src=$fullUrl width=600 height=350/>")
                }
                matcher.appendTail(result)
                result.toString()
            } ?: ""
        }
    }
}
