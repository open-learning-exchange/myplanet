package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getCourseSteps
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatingsById
import org.ole.planet.myplanet.model.RealmStepExam.Companion.getNoOfExam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class CourseDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private lateinit var fragmentCourseDetailBinding: FragmentCourseDetailBinding
    lateinit var dbService: DatabaseService
    private lateinit var cRealm: Realm
    var courses: RealmMyCourse? = null
    var user: RealmUserModel? = null
    var id: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            id = requireArguments().getString("courseId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCourseDetailBinding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        dbService = DatabaseService(requireActivity())
        cRealm = dbService.realmInstance
        courses = cRealm.where(RealmMyCourse::class.java).equalTo("courseId", id).findFirst()
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
        fragmentCourseDetailBinding.noOfExams.text = context?.getString(R.string.number_placeholder, getNoOfExam(cRealm, id))
        val resources: List<RealmMyLibrary> = cRealm.where(RealmMyLibrary::class.java).equalTo("courseId", id).equalTo("resourceOffline", false).isNotNull("resourceLocalAddress").findAll()
        setResourceButton(resources, fragmentCourseDetailBinding.btnResources)
        val downloadedResources: List<RealmMyLibrary> = cRealm.where(RealmMyLibrary::class.java).equalTo("resourceOffline", true).equalTo("courseId", id).isNotNull("resourceLocalAddress").findAll()
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
        val steps = getCourseSteps(cRealm, courses?.courseId)
        fragmentCourseDetailBinding.stepsList.layoutManager = LinearLayoutManager(activity)
        fragmentCourseDetailBinding.stepsList.adapter = AdapterSteps(requireActivity(), steps, cRealm)
    }

    override fun onRatingChanged() {
        val `object` = getRatingsById(cRealm, "course", courses?.courseId, user?.id)
        setRatings(`object`)
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        setCourseData()
    }
}
