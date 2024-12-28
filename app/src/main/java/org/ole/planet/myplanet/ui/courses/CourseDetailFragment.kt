package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.kotlin.Realm
import kotlinx.coroutines.*
import org.ole.planet.myplanet.*
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class CourseDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private lateinit var fragmentCourseDetailBinding: FragmentCourseDetailBinding
    lateinit var dbService: DatabaseService
    private lateinit var cRealm: Realm
    var courses: RealmMyCourse? = null
    var user: RealmUserModel? = null
    var id: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            id = requireArguments().getString("courseId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCourseDetailBinding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        dbService = DatabaseService()
        cRealm = dbService.realmInstance
        courses = mRealm.query<RealmMyCourse>(RealmMyCourse::class, "courseId == $0", id ?: "")
            .first()
            .find()
        user = UserProfileDbHandler(requireContext()).userModel
        return fragmentCourseDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRatingView("course", courses?.courseId, courses?.courseTitle, this)
        setCourseData()
    }

    private fun setCourseData() {
        setTextViewVisibility(fragmentCourseDetailBinding.subjectLevel, courses?.subjectLevel, fragmentCourseDetailBinding.ltSubjectLevel)
        setTextViewVisibility(fragmentCourseDetailBinding.method, courses?.method, fragmentCourseDetailBinding.ltMethod)
        setTextViewVisibility(fragmentCourseDetailBinding.gradeLevel, courses?.gradeLevel, fragmentCourseDetailBinding.ltGradeLevel)
        setTextViewVisibility(fragmentCourseDetailBinding.language, courses?.languageOfInstruction, fragmentCourseDetailBinding.ltLanguage)
        val markdownContentWithLocalPaths = CourseStepFragment.prependBaseUrlToImages(courses?.description, "file://" + MainApplication.context.getExternalFilesDir(null) + "/ole/")
        setMarkdownText(fragmentCourseDetailBinding.description, markdownContentWithLocalPaths)
        scope.launch {
            val examCount = RealmStepExam.getNoOfExam(mRealm, id)
            fragmentCourseDetailBinding.noOfExams.text = context?.getString(R.string.number_placeholder, examCount)
        }
        val resources = mRealm.query<RealmMyLibrary>(RealmMyLibrary::class,
            "courseId == $0 AND resourceOffline == false AND resourceLocalAddress != null", id ?: ""
        ).find()
        setResourceButton(resources, fragmentCourseDetailBinding.btnResources)
        val downloadedResources = mRealm.query<RealmMyLibrary>(RealmMyLibrary::class,
            "courseId == $0 AND resourceOffline == true AND resourceLocalAddress != null",
            id ?: ""
        ).find()
        setOpenResourceButton(downloadedResources, fragmentCourseDetailBinding.btnOpen)
        onRatingChanged()
        setStepsList()
    }

    private fun setTextViewVisibility(textView: TextView, content: String?, layout: View) {
        if (content?.isEmpty() == true) {
            layout.visibility = View.GONE
        } else {
            textView.text = content
        }
    }

    private fun setStepsList() {
        val steps = RealmMyCourse.getCourseSteps(cRealm, courses?.courseId)
        fragmentCourseDetailBinding.stepsList.layoutManager = LinearLayoutManager(activity)
        fragmentCourseDetailBinding.stepsList.adapter = AdapterSteps(requireActivity(), steps, cRealm)
    }

    override fun onRatingChanged() {
        val `object` = RealmRating.getRatingsById(cRealm, "course", courses?.courseId, user?.id)
        setRatings(`object`)
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        setCourseData()
    }
}
